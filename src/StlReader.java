import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

public class StlReader {
    public static ArrayList<Triangle> load(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());

        if (isBinaryStl(data)) {
            return parseBinaryStl(data);
        }

        return parseAsciiStl(data);
    }

    public static Vector3 dimensions(ArrayList<Triangle> mesh) {
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
            buffer.position(buffer.position() + 12);
            Vector3 v1 = readBinaryVertex(buffer);
            Vector3 v2 = readBinaryVertex(buffer);
            Vector3 v3 = readBinaryVertex(buffer);
            triangles.add(new Triangle(v1, v2, v3));
            buffer.position(buffer.position() + 2);
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
