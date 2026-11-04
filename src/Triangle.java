public class Triangle {

    private Vector3 v1;
    private Vector3 v2;
    private Vector3 v3;

    public Triangle(Vector3 v1,
                    Vector3 v2,
                    Vector3 v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public double area() {

        Vector3 a = v2.subtract(v1);
        Vector3 b = v3.subtract(v1);

        return 0.5 * a.cross(b).magnitude();
    }

    public Vector3 normal() {

        Vector3 a = v2.subtract(v1);
        Vector3 b = v3.subtract(v1);

        return a.cross(b);
    }

    public Vector3 vertex(int index) {
        if (index == 0) {
            return v1;
        }
        if (index == 1) {
            return v2;
        }
        if (index == 2) {
            return v3;
        }
        throw new IndexOutOfBoundsException("Triangle vertex index must be 0, 1, or 2.");
    }

    public Vector3 centroid() {
        return new Vector3(
            (v1.x + v2.x + v3.x) / 3.0,
            (v1.y + v2.y + v3.y) / 3.0,
            (v1.z + v2.z + v3.z) / 3.0
        );
    }

    public Vector3 minimum() {
        return new Vector3(
            Math.min(v1.x, Math.min(v2.x, v3.x)),
            Math.min(v1.y, Math.min(v2.y, v3.y)),
            Math.min(v1.z, Math.min(v2.z, v3.z))
        );
    }

    public Vector3 maximum() {
        return new Vector3(
            Math.max(v1.x, Math.max(v2.x, v3.x)),
            Math.max(v1.y, Math.max(v2.y, v3.y)),
            Math.max(v1.z, Math.max(v2.z, v3.z))
        );
    }
}