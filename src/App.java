import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

public class App {
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
        ArrayList<Triangle> mesh = loadStl(new File(path));

        System.out.println("Triangles loaded: " + mesh.size());

        if (mesh.isEmpty()) {
            System.out.println("No valid triangles were found in the STL file.");
            scan.close();
            return;
        }

        System.out.println("Please insert radar wavelength in meters:");
        double lambda = scan.nextDouble();
        scan.nextLine();

        if (lambda <= 0.0) {
            throw new IllegalArgumentException("Radar wavelength must be greater than zero.");
        }

        Vector3 radarDirection = new Vector3(1, 0, 0).normalize();
        double totalRCS = 0.0;

        for (Triangle tri : mesh) {
            double area = tri.area();
            if (area <= 1e-12) {
                continue;
            }

            Vector3 normal = tri.normal();
            double normalMagnitude = normal.magnitude();
            if (normalMagnitude <= 1e-12) {
                continue;
            }

            double cosTheta = Math.abs(normal.dot(radarDirection) / normalMagnitude);
            cosTheta = Math.max(0.0, Math.min(1.0, cosTheta));

            double triangleRCS =
                (4.0 * Math.PI
                * area * area
                / (lambda * lambda))
                * cosTheta * cosTheta;

            totalRCS += triangleRCS;
        }

        System.out.println("RCS = " + totalRCS + " m²");
        scan.close();

        if (totalRCS < 0.0001) {
            System.out.println("Object is stealthier than the F-22 Raptor and the F-35 Lightning II");
        } else if (totalRCS < 0.003) {
            System.out.println("Object is stealthier than the F-117 Nighthawk");
        } else if (totalRCS < 0.1) {
            System.out.println("Object is stealthier than the B-2 Spirit");
        } else {
            System.out.println("Object is not stealthy");
        }
    }

    private static ArrayList<Triangle> loadStl(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());

        if (isBinaryStl(data)) {
            return parseBinaryStl(data);
        }

        return parseAsciiStl(data);
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