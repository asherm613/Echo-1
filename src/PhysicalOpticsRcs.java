import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class PhysicalOpticsRcs {
    private static final int LEAF_SIZE = 8;

    private PhysicalOpticsRcs() {
    }

    public static Result calculate(List<Triangle> mesh, double wavelength,
                                   double metersPerUnit, Vector3 lookDirection) {
        if (mesh == null || mesh.isEmpty()) {
            throw new IllegalArgumentException("At least one mesh triangle is required.");
        }
        if (!Double.isFinite(wavelength) || wavelength <= 0.0) {
            throw new IllegalArgumentException("Wavelength must be finite and greater than zero.");
        }
        if (!Double.isFinite(metersPerUnit) || metersPerUnit <= 0.0) {
            throw new IllegalArgumentException("Mesh scale must be finite and greater than zero.");
        }
        if (lookDirection == null) {
            throw new IllegalArgumentException("Radar look direction is required.");
        }
        double directionLength = lookDirection.magnitude();
        if (!Double.isFinite(directionLength) || directionLength == 0.0) {
            throw new IllegalArgumentException("Radar look direction must be finite and nonzero.");
        }
        lookDirection = lookDirection.normalize();

        Triangle[] triangles = mesh.toArray(new Triangle[0]);
        Bounds bounds = new Bounds(triangles);
        Node acceleration = Node.build(triangles);
        double orientation = signedVolume(triangles) < 0.0 ? -1.0 : 1.0;
        double rayOffset = Math.max(bounds.diagonal() * 1e-9, 1e-12);
        double q = 4.0 * Math.PI * metersPerUnit / wavelength;
        double real = 0.0;
        double imaginary = 0.0;
        int illuminated = 0;
        int shadowed = 0;

        for (Triangle triangle : triangles) {
            double area = triangle.area();
            Vector3 rawNormal = triangle.normal();
            double normalLength = rawNormal.magnitude();
            if (area <= 0.0 || normalLength <= 0.0) {
                continue;
            }

            Vector3 normal = new Vector3(
                orientation * rawNormal.x / normalLength,
                orientation * rawNormal.y / normalLength,
                orientation * rawNormal.z / normalLength
            );
            double incidence = normal.dot(lookDirection);
            if (incidence <= 0.0) {
                continue;
            }

            Vector3 centroid = triangle.centroid();
            Vector3 rayOrigin = new Vector3(
                centroid.x + lookDirection.x * rayOffset,
                centroid.y + lookDirection.y * rayOffset,
                centroid.z + lookDirection.z * rayOffset
            );
            if (acceleration.isOccluded(rayOrigin, lookDirection, triangle, rayOffset)) {
                shadowed++;
                continue;
            }

            Complex integral = triangleIntegral(triangle, lookDirection, q);
            double weight = area * incidence * metersPerUnit * metersPerUnit;
            real += weight * integral.real;
            imaginary += weight * integral.imaginary;
            illuminated++;
        }

        double rcs = (4.0 * Math.PI / (wavelength * wavelength))
            * (real * real + imaginary * imaginary);
        return new Result(rcs, illuminated, shadowed,
            maximumEdgeLength(triangles) * metersPerUnit);
    }

    private static Complex triangleIntegral(Triangle triangle, Vector3 direction, double q) {
        double[] phase = new double[3];
        for (int i = 0; i < phase.length; i++) {
            phase[i] = q * direction.dot(triangle.vertex(i));
        }

        double mean = (phase[0] + phase[1] + phase[2]) / 3.0;
        for (int i = 0; i < phase.length; i++) {
            phase[i] -= mean;
        }
        Arrays.sort(phase);

        if (phase[2] - phase[0] < 1e-3) {
            return new Complex(
                Math.cos(q * direction.dot(triangle.centroid())),
                Math.sin(q * direction.dot(triangle.centroid()))
            );
        }

        Complex first = exponentialDividedDifference(phase[0], phase[1]);
        Complex second = exponentialDividedDifference(phase[1], phase[2]);
        Complex dividedDifference = second.subtract(first).scale(1.0 / (phase[2] - phase[0]));
        // The triangle integral is the second divided difference of exp(i phase).
        Complex centeredIntegral = dividedDifference.scale(-2.0);
        double centerPhase = q * direction.dot(triangle.centroid());
        return centeredIntegral.rotate(centerPhase);
    }

    private static Complex exponentialDividedDifference(double first, double second) {
        double halfDifference = (second - first) / 2.0;
        double sinc = Math.abs(halfDifference) < 1e-8
            ? 1.0 - halfDifference * halfDifference / 6.0
            : Math.sin(halfDifference) / halfDifference;
        double midpoint = (first + second) / 2.0;
        return new Complex(
            -Math.sin(midpoint) * sinc,
            Math.cos(midpoint) * sinc
        );
    }

    private static double signedVolume(Triangle[] triangles) {
        Vector3 reference = triangles[0].vertex(0);
        double volumeTimesSix = 0.0;
        for (Triangle triangle : triangles) {
            Vector3 first = triangle.vertex(0).subtract(reference);
            Vector3 second = triangle.vertex(1).subtract(reference);
            Vector3 third = triangle.vertex(2).subtract(reference);
            volumeTimesSix += first.dot(second.cross(third));
        }
        return volumeTimesSix / 6.0;
    }

    private static double maximumEdgeLength(Triangle[] triangles) {
        double maximum = 0.0;
        for (Triangle triangle : triangles) {
            for (int i = 0; i < 3; i++) {
                maximum = Math.max(maximum,
                    triangle.vertex(i).subtract(triangle.vertex((i + 1) % 3)).magnitude());
            }
        }
        return maximum;
    }

    public static final class Result {
        public final double rcs;
        public final int illuminatedFacets;
        public final int shadowedFacets;
        public final double maximumEdgeLengthMeters;

        private Result(double rcs, int illuminatedFacets, int shadowedFacets,
                       double maximumEdgeLengthMeters) {
            this.rcs = rcs;
            this.illuminatedFacets = illuminatedFacets;
            this.shadowedFacets = shadowedFacets;
            this.maximumEdgeLengthMeters = maximumEdgeLengthMeters;
        }
    }

    private static final class Complex {
        private final double real;
        private final double imaginary;

        private Complex(double real, double imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }

        private Complex subtract(Complex other) {
            return new Complex(real - other.real, imaginary - other.imaginary);
        }

        private Complex scale(double factor) {
            return new Complex(real * factor, imaginary * factor);
        }

        private Complex rotate(double angle) {
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            return new Complex(
                real * cosine - imaginary * sine,
                real * sine + imaginary * cosine
            );
        }
    }

    private static final class Bounds {
        private final Vector3 minimum;
        private final Vector3 maximum;

        private Bounds(Triangle[] triangles) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Triangle triangle : triangles) {
                Vector3 min = triangle.minimum();
                Vector3 max = triangle.maximum();
                minX = Math.min(minX, min.x);
                minY = Math.min(minY, min.y);
                minZ = Math.min(minZ, min.z);
                maxX = Math.max(maxX, max.x);
                maxY = Math.max(maxY, max.y);
                maxZ = Math.max(maxZ, max.z);
            }
            minimum = new Vector3(minX, minY, minZ);
            maximum = new Vector3(maxX, maxY, maxZ);
        }

        private double diagonal() {
            return maximum.subtract(minimum).magnitude();
        }
    }

    private static final class Node {
        private final Vector3 minimum;
        private final Vector3 maximum;
        private final Node left;
        private final Node right;
        private final Triangle[] leaf;

        private Node(Vector3 minimum, Vector3 maximum, Node left, Node right,
                     Triangle[] leaf) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.left = left;
            this.right = right;
            this.leaf = leaf;
        }

        private static Node build(Triangle[] triangles) {
            if (triangles.length <= LEAF_SIZE) {
                return new Node(minimum(triangles), maximum(triangles), null, null, triangles);
            }

            Vector3 min = minimum(triangles);
            Vector3 max = maximum(triangles);
            int axis = largestAxis(max.subtract(min));
            Arrays.sort(triangles, Comparator.comparingDouble(t -> coordinate(t.centroid(), axis)));
            int middle = triangles.length / 2;
            Node left = build(Arrays.copyOfRange(triangles, 0, middle));
            Node right = build(Arrays.copyOfRange(triangles, middle, triangles.length));
            return new Node(min, max, left, right, null);
        }

        private boolean isOccluded(Vector3 origin, Vector3 direction, Triangle source,
                                   double rayOffset) {
            if (!intersectsBounds(origin, direction, rayOffset)) {
                return false;
            }
            if (leaf != null) {
                for (Triangle triangle : leaf) {
                    if (triangle != source && intersectsTriangle(origin, direction, triangle,
                        rayOffset)) {
                        return true;
                    }
                }
                return false;
            }
            return left.isOccluded(origin, direction, source, rayOffset)
                || right.isOccluded(origin, direction, source, rayOffset);
        }

        private boolean intersectsBounds(Vector3 origin, Vector3 direction, double tolerance) {
            double near = 0.0;
            double far = Double.POSITIVE_INFINITY;
            for (int axis = 0; axis < 3; axis++) {
                double o = coordinate(origin, axis);
                double d = coordinate(direction, axis);
                double min = coordinate(minimum, axis);
                double max = coordinate(maximum, axis);
                if (Math.abs(d) < 1e-15) {
                    if (o < min - tolerance || o > max + tolerance) {
                        return false;
                    }
                } else {
                    double first = (min - tolerance - o) / d;
                    double second = (max + tolerance - o) / d;
                    if (first > second) {
                        double swap = first;
                        first = second;
                        second = swap;
                    }
                    near = Math.max(near, first);
                    far = Math.min(far, second);
                    if (far < near) {
                        return false;
                    }
                }
            }
            return far > 0.0;
        }

        private static boolean intersectsTriangle(Vector3 origin, Vector3 direction,
                                                  Triangle triangle, double rayOffset) {
            Vector3 edge1 = triangle.vertex(1).subtract(triangle.vertex(0));
            Vector3 edge2 = triangle.vertex(2).subtract(triangle.vertex(0));
            Vector3 p = direction.cross(edge2);
            double determinant = edge1.dot(p);
            double determinantTolerance = 1e-12 * edge1.magnitude() * edge2.magnitude();
            if (Math.abs(determinant) <= determinantTolerance) {
                return false;
            }

            double inverseDeterminant = 1.0 / determinant;
            Vector3 fromVertex = origin.subtract(triangle.vertex(0));
            double u = fromVertex.dot(p) * inverseDeterminant;
            if (u < -1e-10 || u > 1.0 + 1e-10) {
                return false;
            }

            Vector3 q = fromVertex.cross(edge1);
            double v = direction.dot(q) * inverseDeterminant;
            if (v < -1e-10 || u + v > 1.0 + 1e-10) {
                return false;
            }
            double distance = edge2.dot(q) * inverseDeterminant;
            return distance > rayOffset;
        }

        private static Vector3 minimum(Triangle[] triangles) {
            double x = Double.POSITIVE_INFINITY;
            double y = Double.POSITIVE_INFINITY;
            double z = Double.POSITIVE_INFINITY;
            for (Triangle triangle : triangles) {
                Vector3 value = triangle.minimum();
                x = Math.min(x, value.x);
                y = Math.min(y, value.y);
                z = Math.min(z, value.z);
            }
            return new Vector3(x, y, z);
        }

        private static Vector3 maximum(Triangle[] triangles) {
            double x = Double.NEGATIVE_INFINITY;
            double y = Double.NEGATIVE_INFINITY;
            double z = Double.NEGATIVE_INFINITY;
            for (Triangle triangle : triangles) {
                Vector3 value = triangle.maximum();
                x = Math.max(x, value.x);
                y = Math.max(y, value.y);
                z = Math.max(z, value.z);
            }
            return new Vector3(x, y, z);
        }

        private static int largestAxis(Vector3 extents) {
            if (extents.x >= extents.y && extents.x >= extents.z) {
                return 0;
            }
            return extents.y >= extents.z ? 1 : 2;
        }

        private static double coordinate(Vector3 vector, int axis) {
            if (axis == 0) {
                return vector.x;
            }
            return axis == 1 ? vector.y : vector.z;
        }
    }
}
