import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

public class App {
    private static final double ASSUMED_METERS_PER_UNIT = 0.1;

    public static String removeChar(String input, String target) {
        int x;
        for (x = 0; x < input.length(); x++) {
        if (input.substring(x, x+1).equals(target))
            return input.substring(0 , x) + input.substring(x+1);
        }
        return input;
        }
    public static void main(String[] args) throws Exception {
        Scanner scan = new Scanner(System.in);
        String path;
        System.out.println("Please insert file path to STL file:");
        String filepath = scan.nextLine();
        if (filepath.startsWith("\"") && filepath.endsWith("\"")) {
            path = filepath.substring(1, filepath.length() - 1);
        }
        else {
            path = filepath;
        }
        File stlFile = new File(path);
        ArrayList<Triangle> mesh = loadStl(stlFile);

        System.out.println("Triangles loaded: " + mesh.size());

        if (mesh.isEmpty()) {
            System.out.println("No valid triangles were found in the STL file.");
            scan.close();
            return;
        }

        Vector3 dimensions = meshDimensions(mesh);
        System.out.printf("STL dimensions (coordinate units): X=%.6f, Y=%.6f, Z=%.6f%n",
            dimensions.x, dimensions.y, dimensions.z);

        double metersPerUnit = ASSUMED_METERS_PER_UNIT;
        System.out.printf("Assuming %.3f meters per STL coordinate unit.%n", metersPerUnit);

        System.out.printf("Estimated model dimensions (meters): X=%.3f, Y=%.3f, Z=%.3f%n",
            dimensions.x * metersPerUnit,
            dimensions.y * metersPerUnit,
            dimensions.z * metersPerUnit);

        System.out.println("Please insert radar wavelength in meters:");
        double lambda = scan.nextDouble();
        scan.nextLine();

        if (!Double.isFinite(lambda) || lambda <= 0.0) {
            throw new IllegalArgumentException("Radar wavelength must be greater than zero.");
        }

        System.out.println("Enter radar look direction as three values on one line (example: 1 0 0).");
        System.out.println("Press Enter to use the default direction +X (1 0 0):");
        String directionInput = scan.nextLine().trim();
        Vector3 radarDirection = parseRadarDirection(directionInput);
        double directionLength = radarDirection.magnitude();
        if (!Double.isFinite(directionLength) || directionLength == 0.0) {
            throw new IllegalArgumentException("Radar direction must be a finite nonzero vector.");
        }
        radarDirection = radarDirection.normalize();

        PhysicalOpticsRcs.Result result = PhysicalOpticsRcs.calculate(
            mesh, lambda, metersPerUnit, radarDirection);
        double totalRCS = result.rcs;

        System.out.printf("Visible facets: %d; shadowed front-facing facets: %d%n",
            result.illuminatedFacets, result.shadowedFacets);
        System.out.printf("Largest STL facet edge: %.6g m (%.3f wavelengths)%n",
            result.maximumEdgeLengthMeters, result.maximumEdgeLengthMeters / lambda);
        if (result.maximumEdgeLengthMeters > lambda / 5.0) {
            System.out.println("WARNING: Mesh facets are large compared with wavelength; "
                + "surface shape and RCS may be inaccurate. Use a finer STL mesh.");
        }
        System.out.println("Model: ideal-conductor, single-bounce physical optics with "
            + "coherent triangular phase integration and centroid shadow testing.");
        System.out.println("It excludes edge diffraction, multiple reflections, material "
            + "properties, and polarization-specific effects; it is not a full-wave solver.");
        if (totalRCS < 0.0001) {
            System.out.println("Estimated RCS is below the F-22 Raptor and F-35 Lightning II comparison threshold.");
        } else if (totalRCS < 0.003) {
            System.out.println("Estimated RCS is below the F-117 Nighthawk comparison threshold.");
        } else if (totalRCS < 0.1) {
            System.out.println("Estimated RCS is below the B-2 Spirit comparison threshold.");
        } else {
            System.out.println("Estimated RCS is above the stealth-aircraft comparison thresholds.");
        }
        System.out.println("These are rough threshold comparisons, not validated measurements of aircraft RCS.");
        System.out.printf("RCS result (monostatic physical-optics estimate): %.6g m²%n", totalRCS);
        scan.close();
    }

    private static Vector3 parseRadarDirection(String input) {
        if (input.isEmpty()) {
            return new Vector3(1.0, 0.0, 0.0);
        }

        String[] components = input.replace(',', ' ').trim().split("\\s+");
        if (components.length != 3) {
            throw new IllegalArgumentException(
                "Enter exactly three direction values, for example: 1 0 0");
        }
        try {
            double x = Double.parseDouble(components[0]);
            double y = Double.parseDouble(components[1]);
            double z = Double.parseDouble(components[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Radar direction values must be finite.");
            }
            return new Vector3(x, y, z);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid radar direction. Enter three numbers, for example: 1 0 0", e);
        }
    }

    private static ArrayList<Triangle> loadStl(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());

        if (isBinaryStl(data)) {
            return parseBinaryStl(data);
        }

        return parseAsciiStl(data);
    }

    private static Vector3 meshDimensions(ArrayList<Triangle> mesh) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Triangle triangle : mesh) {
            Vector3 min = triangle.minimum();
            Vector3 max = triangle.maximum();
            minX = Math.min(minX, min.x);
            minY = Math.min(minY, min.y);
            minZ = Math.min(minZ, min.z);
            maxX = Math.max(maxX, max.x);
            maxY = Math.max(maxY, max.y);
            maxZ = Math.max(maxZ, max.z);
        }

        return new Vector3(maxX - minX, maxY - minY, maxZ - minZ);
    }

    private static boolean isBinaryStl(byte[] data) {
        if (data.length < 84) {
            return false;
        }

        ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long triangleCount = Integer.toUnsignedLong(header.getInt(80));
        return 84L + 50L * triangleCount == data.length;
    }

    private static ArrayList<Triangle> parseBinaryStl(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long triangleCount = Integer.toUnsignedLong(buffer.getInt(80));
        ArrayList<Triangle> triangles = new ArrayList<>((int) triangleCount);

        buffer.position(84);
        for (long i = 0; i < triangleCount; i++) {
            buffer.position(buffer.position() + 12); // Skip the stored facet normal.
            Vector3 v1 = readBinaryVertex(buffer);
            Vector3 v2 = readBinaryVertex(buffer);
            Vector3 v3 = readBinaryVertex(buffer);
            triangles.add(new Triangle(v1, v2, v3));
            buffer.position(buffer.position() + 2); // Skip the attribute byte count.
        }

        return triangles;
    }

    private static Vector3 readBinaryVertex(ByteBuffer buffer) throws IOException {
        double x = buffer.getFloat();
        double y = buffer.getFloat();
        double z = buffer.getFloat();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IOException("STL contains a non-finite vertex coordinate.");
        }
        return new Vector3(x, y, z);
    }

    private static ArrayList<Triangle> parseAsciiStl(byte[] data) throws IOException {
        ArrayList<Triangle> triangles = new ArrayList<>();
        ArrayList<Vector3> vertices = new ArrayList<>(3);

        try (Scanner stl = new Scanner(new String(data, java.nio.charset.StandardCharsets.US_ASCII))) {
            while (stl.hasNextLine()) {
                String line = stl.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (!parts[0].equalsIgnoreCase("vertex")) {
                    continue;
                }
                if (parts.length != 4) {
                    throw new IOException("Malformed vertex line in ASCII STL: " + line);
                }

                try {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        throw new IOException("STL contains a non-finite vertex coordinate.");
                    }
                    vertices.add(new Vector3(x, y, z));
                } catch (NumberFormatException e) {
                    throw new IOException("Malformed vertex coordinates in ASCII STL: " + line, e);
                }

                if (vertices.size() == 3) {
                    triangles.add(new Triangle(vertices.get(0), vertices.get(1), vertices.get(2)));
                    vertices.clear();
                }
            }
        }

        if (!vertices.isEmpty()) {
            throw new IOException("ASCII STL ended with an incomplete triangle.");
        }
        if (triangles.isEmpty()) {
            throw new IOException("No triangles were found. The file is not a readable ASCII or binary STL.");
        }

        return triangles;
        }
    }