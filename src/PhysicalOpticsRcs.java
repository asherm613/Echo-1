import java.util.ArrayList;

public class PhysicalOpticsRcs {
    public static double estimate(ArrayList<Triangle> triangles, double wavelength,
                                  double metersPerUnit, Vector3 radarDirection) {
        radarDirection = radarDirection.normalize();
        double orientation = getMeshOrientation(triangles);
        double rayOffset = 1e-6;
        double waveNumber = 4.0 * Math.PI / wavelength;
        double realTotal = 0.0;
        double imaginaryTotal = 0.0;

        for (Triangle triangle : triangles) {
            double area = triangle.area();
            Vector3 normal = triangle.normal();
            double normalLength = normal.magnitude();
            if (area == 0.0 || normalLength == 0.0) {
                continue;
            }

            double facing = orientation * normal.dot(radarDirection) / normalLength;
            if (facing <= 0.0) {
                continue;
            }

            Vector3 center = triangle.centroid();
            Vector3 rayStart = new Vector3(
                center.x + radarDirection.x * rayOffset,
                center.y + radarDirection.y * rayOffset,
                center.z + radarDirection.z * rayOffset);
            if (isBlocked(rayStart, radarDirection, triangle, triangles, rayOffset)) {
                continue;
            }

            Complex phaseAverage = getPhaseAverage(triangle, radarDirection,
                waveNumber * metersPerUnit);
            double facetArea = area * metersPerUnit * metersPerUnit * facing;
            realTotal += facetArea * phaseAverage.real;
            imaginaryTotal += facetArea * phaseAverage.imaginary;
        }

        return 4.0 * Math.PI / (wavelength * wavelength)
            * (realTotal * realTotal + imaginaryTotal * imaginaryTotal);
    }

    private static double getMeshOrientation(ArrayList<Triangle> triangles) {
        Vector3 reference = triangles.get(0).vertex(0);
        double volume = 0.0;
        for (Triangle triangle : triangles) {
            Vector3 a = triangle.vertex(0).subtract(reference);
            Vector3 b = triangle.vertex(1).subtract(reference);
            Vector3 c = triangle.vertex(2).subtract(reference);
            volume += a.dot(b.cross(c));
        }
        return volume < 0.0 ? -1.0 : 1.0;
    }

    private static Complex getPhaseAverage(Triangle triangle, Vector3 direction,
                                           double waveNumber) {
        double[] phase = new double[3];
        for (int i = 0; i < 3; i++) {
            phase[i] = waveNumber * direction.dot(triangle.vertex(i));
        }
        double center = (phase[0] + phase[1] + phase[2]) / 3.0;
        for (int i = 0; i < 3; i++) {
            phase[i] -= center;
        }
        java.util.Arrays.sort(phase);

        if (phase[2] - phase[0] < 0.001) {
            return new Complex(Math.cos(center), Math.sin(center));
        }

        Complex first = exponentialDifference(phase[0], phase[1]);
        Complex second = exponentialDifference(phase[1], phase[2]);
        double divisor = phase[2] - phase[0];
        double real = -2.0 * (second.real - first.real) / divisor;
        double imaginary = -2.0 * (second.imaginary - first.imaginary) / divisor;

        return new Complex(
            real * Math.cos(center) - imaginary * Math.sin(center),
            real * Math.sin(center) + imaginary * Math.cos(center));
    }

    private static Complex exponentialDifference(double first, double second) {
        double halfDifference = (second - first) / 2.0;
        double sinc = 1.0;
        if (Math.abs(halfDifference) >= 0.000001) {
            sinc = Math.sin(halfDifference) / halfDifference;
        }
        double middle = (first + second) / 2.0;
        return new Complex(-Math.sin(middle) * sinc, Math.cos(middle) * sinc);
    }

    private static boolean isBlocked(Vector3 rayStart, Vector3 direction,
                                     Triangle source, ArrayList<Triangle> triangles,
                                     double rayOffset) {
        for (Triangle other : triangles) {
            if (other != source && rayHitsTriangle(rayStart, direction, other, rayOffset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rayHitsTriangle(Vector3 start, Vector3 direction,
                                           Triangle triangle, double minimumDistance) {
        Vector3 edge1 = triangle.vertex(1).subtract(triangle.vertex(0));
        Vector3 edge2 = triangle.vertex(2).subtract(triangle.vertex(0));
        Vector3 cross = direction.cross(edge2);
        double determinant = edge1.dot(cross);
        if (Math.abs(determinant) < 1e-12) {
            return false;
        }

        Vector3 fromVertex = start.subtract(triangle.vertex(0));
        double u = fromVertex.dot(cross) / determinant;
        Vector3 secondCross = fromVertex.cross(edge1);
        double v = direction.dot(secondCross) / determinant;
        double distance = edge2.dot(secondCross) / determinant;

        return u >= 0.0 && v >= 0.0 && u + v <= 1.0
            && distance > minimumDistance;
    }

    private static class Complex {
        private final double real;
        private final double imaginary;

        private Complex(double real, double imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }
    }
}
