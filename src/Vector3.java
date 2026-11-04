public class Vector3 {

    public double x;
    public double y;
    public double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double dot(Vector3 other) {
        return x * other.x +
               y * other.y +
               z * other.z;
    }

    public double magnitude() {
        return Math.sqrt(x*x + y*y + z*z);
    }

    public Vector3 normalize() {
        double len = magnitude();
        if (len == 0.0) {
            return new Vector3(0.0, 0.0, 0.0);
        }
        return new Vector3(x / len, y / len, z / len);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(
            x - other.x,
            y - other.y,
            z - other.z
        );
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }
}