import java.util.Scanner;
import java.util.ArrayList;
public class App {
    public static void main(String[] args) throws Exception {
        ArrayList<Double> rcsval = new ArrayList<>();
            double theta;
            double phi;
            double finalanswer = 0.0;
            boolean state = true;
        while (state) {
            Scanner scan = new Scanner(System.in);
            System.out.println("Enter object length in meters");
            double length= scan.nextDouble();     // meters
            System.out.println("Enter object width in meters");
            double width = scan.nextDouble();      // meters
            System.out.println("Enter Elevation angle");
            double thetaDeg = scan.nextDouble();     // degrees
            System.out.println("Enter Azimuth angle");
            double phiDeg = scan.nextDouble();       // degrees
            System.out.println("Enter radar wavelength in meters");
            double lambda = scan.nextDouble();    // meters 

            theta = Math.toRadians(thetaDeg);
            phi = Math.toRadians(phiDeg);

            double x = (2.0 * Math.PI * length / lambda)
                    * Math.sin(theta)
                    * Math.cos(phi);

            double y = (2.0 * Math.PI * width / lambda)
                    * Math.sin(phi);

            // sinc(z) = sin(z)/z, handle z=0
            double sincX = (Math.abs(x) < 1e-10) ? 1.0 : Math.sin(x) / x;
            double sincY = (Math.abs(y) < 1e-10) ? 1.0 : Math.sin(y) / y;

            double rcs =
                    (4.0 * Math.PI * length * length * width * width)
                    / (lambda * lambda)
                    * Math.pow(Math.cos(theta), 2)
                    * Math.pow(sincX, 2)
                    * Math.pow(sincY, 2);
                rcsval.add(rcs);
            for (double w = 0.0; w < rcsval.size(); w++) {
                finalanswer += rcsval.get((int) w);
            }
            System.out.println("RCS = " + finalanswer + " m²");
            System.out.print("Continue? (y/n): ");
            String input = scan.nextLine();
            input = scan.nextLine();
            if (input.equals("y") || input.equals("Y")) {
                state = true;
            } else {
                state = false;
            }
        }
        if (finalanswer < 0.0001){
            System.out.println("Object is stealthier than the F22 Raptor and the F35 Lightning II");
        }
        else if (finalanswer < 0.003) {
            System.out.println("Object is stealthier than the F117 Night Hawk\nYou've got a truly hopeless diamond");
        } 
        else if (finalanswer < 0.1){
            System.out.println("Object is stealthier than the B2 Spirit");
        }
        else {
            System.out.println("Object is not stealthy");
        }
    }
}
