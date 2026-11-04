import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class App {
    private static final double ASSUMED_METERS_PER_UNIT = 0.1;

    public static void main(String[] args) throws Exception {
        Scanner scan = new Scanner(System.in);
        System.out.println("Please insert file path to STL file:");
        String filepath = scan.nextLine();
        if (filepath.startsWith("\"") && filepath.endsWith("\"")) {
            filepath = filepath.substring(1, filepath.length() - 1);
        }
        File stlFile = new File(filepath);
        ArrayList<Triangle> mesh = StlReader.load(stlFile);

        System.out.println("Triangles loaded: " + mesh.size());

        if (mesh.isEmpty()) {
            System.out.println("No valid triangles were found in the STL file.");
            scan.close();
            return;
        }

        Vector3 dimensions = StlReader.dimensions(mesh);
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

        System.out.println("Enter the radar look direction. Use 1, 0, 0 for the default +X direction.");
        System.out.print("X direction: ");
        double directionX = scan.nextDouble();
        System.out.print("Y direction: ");
        double directionY = scan.nextDouble();
        System.out.print("Z direction: ");
        double directionZ = scan.nextDouble();

        Vector3 radarDirection = new Vector3(directionX, directionY, directionZ);
        if (!Double.isFinite(directionX) || !Double.isFinite(directionY)
            || !Double.isFinite(directionZ) || radarDirection.magnitude() == 0.0) {
            throw new IllegalArgumentException("Radar direction must be a finite nonzero vector.");
        }
        radarDirection = radarDirection.normalize();

        double totalRCS = PhysicalOpticsRcs.estimate(
            mesh, lambda, metersPerUnit, radarDirection);

        System.out.println("RCS model: ideal-conductor physical-optics estimate.");
        System.out.println("It includes coherent facet phase and approximate shadowing, "
            + "but not diffraction, polarization, material, or multiple reflections.");
        System.out.println("RCS accuracy depends on mesh quality and radar conditions; "
            + "this is not a guaranteed-accuracy full-wave calculation.");
        printAircraftComparison(totalRCS);
        System.out.println("These are rough threshold comparisons, not validated measurements of aircraft RCS.");
        System.out.printf("RCS result (rough estimate): %.6g m²%n", totalRCS);
        scan.close();
    }

    private static void printAircraftComparison(double rcs) {
        if (rcs < 0.0001) {
            System.out.println("Estimated RCS is below the F-22 Raptor and F-35 Lightning II comparison threshold.");
        } else if (rcs < 0.003) {
            System.out.println("Estimated RCS is below the F-117 Nighthawk comparison threshold.");
        } else if (rcs < 0.1) {
            System.out.println("Estimated RCS is below the B-2 Spirit comparison threshold.");
        } else {
            System.out.println("Estimated RCS is above the stealth-aircraft comparison thresholds.");
        }
    }

}