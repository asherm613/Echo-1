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
}