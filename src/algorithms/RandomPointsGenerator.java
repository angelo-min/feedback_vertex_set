package algorithms;

import java.awt.Point;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class RandomPointsGenerator {
    private static String filename = "input.points";
    private static int numberOfPoints = 150;
    private static int maxWidth = 1400;
    private static int maxHeight = 900;
    private static int radius = 140;
 
    public RandomPointsGenerator() {
    }
 
    public static double distanceToCenter(int x, int y) {
       return Math.min(Math.min(Math.min(Math.sqrt(Math.pow((double)(x - maxWidth / 2), 2.0) + Math.pow((double)(y - maxHeight / 2), 2.0)), Math.sqrt(Math.pow((double)x - 2.5 * (double)maxWidth / 6.0, 2.0) + Math.pow((double)(y - 2 * maxHeight / 6), 2.0))), Math.min(Math.sqrt(Math.pow((double)(x - 4 * maxWidth / 6), 2.0) + Math.pow((double)(y - 2 * maxHeight / 6), 2.0)), Math.sqrt(Math.pow((double)(x - 2 * maxWidth / 6), 2.0) + Math.pow((double)(y - 4 * maxHeight / 6), 2.0)))), Math.sqrt(Math.pow((double)(x - 4 * maxWidth / 6), 2.0) + Math.pow((double)(y - 4 * maxHeight / 6), 2.0)));
    }
 
    public static void main(String[] args) {
       try {
          for(int index = 0; index < 100; ++index) {
             PrintStream output = new PrintStream(new FileOutputStream("tests/input" + index + ".points"));
             Random generator = new Random();
             ArrayList<Point> points = new ArrayList();
             int edgeThreshold = 48;
 
             for(int i = 0; i < numberOfPoints; ++i) {
                int x;
                int y;
                int deg;
                do {
                   do {
                      x = generator.nextInt(maxWidth);
                      y = generator.nextInt(maxHeight);
                   } while(!(distanceToCenter(x, y) < (double)radius * 1.4) && (!(distanceToCenter(x, y) < (double)radius * 1.6) || generator.nextInt(5) != 1) && (!(distanceToCenter(x, y) < (double)radius * 1.8) || generator.nextInt(10) != 1) && (maxHeight / 9 >= x || x >= 4 * maxHeight / 5 || maxHeight / 9 >= y || y >= 7 * maxHeight / 9 || generator.nextInt(100) != 1));
 
                   Point p = new Point(x, y);
                   deg = 0;
                   Iterator var11 = points.iterator();
 
                   while(var11.hasNext()) {
                      Point q = (Point)var11.next();
                      if (p.distance(q) <= (double)edgeThreshold) {
                         ++deg;
                      }
                   }
                } while(deg >= 5);
 
                points.add(new Point(x, y));
                output.println(Integer.toString(x) + " " + Integer.toString(y));
             }
 
             output.close();
          }
       } catch (FileNotFoundException var13) {
          System.err.println("I/O exception: unable to create " + filename);
       }
 
    }
 
    public static void generate(int nbPoints) {
       try {
          PrintStream output = new PrintStream(new FileOutputStream(filename));
          Random generator = new Random();
 
          for(int i = 0; i < nbPoints; ++i) {
             int x;
             int y;
             do {
                x = generator.nextInt(maxWidth);
                y = generator.nextInt(maxHeight);
             } while(!(distanceToCenter(x, y) < (double)radius * 1.4) && (!(distanceToCenter(x, y) < (double)radius * 1.6) || generator.nextInt(5) != 1) && (!(distanceToCenter(x, y) < (double)radius * 1.8) || generator.nextInt(10) != 1) && (maxHeight / 5 >= x || x >= 4 * maxHeight / 5 || maxHeight / 5 >= y || y >= 4 * maxHeight / 5 || generator.nextInt(100) != 1));
 
             output.println(Integer.toString(x) + " " + Integer.toString(y));
          }
 
          output.close();
       } catch (FileNotFoundException var6) {
          System.err.println("I/O exception: unable to create " + filename);
       }
 
    }
 }
 