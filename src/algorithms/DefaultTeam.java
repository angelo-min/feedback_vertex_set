package algorithms;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DefaultTeam {

  public ArrayList<Point> calculFVS(ArrayList<Point> _points, int edgeThreshold) {
    HashSet<Point> points = new HashSet<>(_points);
    ArrayList<Point> result = null;
    ArrayList<Point> greed = greedy(points,edgeThreshold);

    for (int i=0;i<3;i++){
      ArrayList<Point> fvs = localSearch(greed,points,edgeThreshold);

      System.out.println("MAIN. Current sol: " + (result == null ? _points.size() : result.size()) + ". Found next sol: "+fvs.size());

      if (result == null || fvs.size()<result.size()) result = fvs;
    }
    
    return new ArrayList<>(result);
    //return greedy(points,edgeThreshold);
  }

  private ArrayList<Point> greedy(HashSet<Point> points, int edgeThreshold) {
    ArrayList<Point> result = null;
    
    for (int i=0;i<100;i++) {
      HashMap<Point, Integer> degrees = new HashMap<>();
      for (Point p: points) degrees.put(p, degree(p, points, edgeThreshold));
      ArrayList<Point> pointsSorted = new ArrayList<>(points);
      pointsSorted.sort((a, b) -> degrees.get(b) - degrees.get(a));
      ArrayList<Point> fvs = new ArrayList<>();


      while (!isSolution(fvs,points,edgeThreshold)) {
        Point choosenOne=pointsSorted.get(0);
        /*for (Point p: pointsSorted) {
          if (degree(p, points, edgeThreshold) >
              degree(choosenOne, points, edgeThreshold)) {
            choosenOne=p;
          }
        }*/
        fvs.add(choosenOne);
        pointsSorted.removeAll(fvs);
      }
      
//      System.out.println("GR. Current sol: " + result.size() + ". Found next sol: "+fvs.size());

      if (result == null || fvs.size()<result.size()) result = fvs;

    }

    return result;
  }
  private ArrayList<Point> localSearch(ArrayList<Point> firstSolution, HashSet<Point> points, int edgeThreshold) {
    ArrayList<Point> current = firstSolution;
    ArrayList<Point> next = current;

    System.out.println("LS. First sol: " + current.size());

    do {
      current = next;
      next = remove2add1(current, points,edgeThreshold);
      System.out.println("LS. Current sol: " + current.size() + ". Found next sol: "+next.size());
    } while (score(current)>score(next));
    
    System.out.println("LS. Last sol: " + current.size());
    return next;

//  return current;
  }
  private ArrayList<Point> remove2add1(ArrayList<Point> candidate, HashSet<Point> points, int edgeThreshold) {
    ArrayList<Point> test = new ArrayList<>(candidate);
    long seed = System.nanoTime();
    Collections.shuffle(test, new Random(seed));
    HashSet<Point> rest = new HashSet<>(points);
    test.forEach(rest::remove);
    HashSet<Point> solutionRest = new HashSet<>(rest);

    for (int i=0;i<test.size();i++) {
      Point p = test.get(i);
      solutionRest.add(p);
      for (int j=i+1;j<test.size();j++) {
        Point q = test.get(j);
        solutionRest.add(q);
        
        for (Point r: rest) {
          solutionRest.remove(r);
          if (isSolution(new HashSet<>(solutionRest),edgeThreshold)) {
            test.remove(j);
            test.remove(i);
            test.add(r);
            return test;
          }
          solutionRest.add(r);
        }

        solutionRest.remove(q);
      }
      solutionRest.remove(p);
    }

    return candidate;
  }
  private boolean isSolution(ArrayList<Point> candidate, HashSet<Point> pointsIn, int edgeThreshold) {
    HashSet<Point> rest = new HashSet<>(pointsIn);
    candidate.forEach(rest::remove);
    return isSolution(rest, edgeThreshold);
  }
  private boolean isSolution(HashSet<Point> rest, int edgeThreshold) {
    ArrayList<Point> visited = new ArrayList<>();

    while (!rest.isEmpty()) {
      visited.clear();
      Iterator<Point> it = rest.iterator();
      visited.add(it.next());
      it.remove();
      for (int i=0;i<visited.size();i++) {
        for (Point p: rest) if (isEdge(visited.get(i),p,edgeThreshold)) {
          for (Point q: visited)
            if (!q.equals(visited.get(i)) && isEdge(p,q,edgeThreshold))
              return false;
          visited.add(p);
        }
        visited.forEach(rest::remove);
      }
    }

    return true;
  }
  private boolean isEdge(Point p, Point q, int edgeThreshold) {
    return p.distance(q)<edgeThreshold;
  }
  private int degree(Point p, Collection<Point> points, int edgeThreshold) {
    int degree=-1;
    for (Point q: points) if (isEdge(p,q,edgeThreshold)) degree++;
    return degree;
  }
  private int score(ArrayList<Point> candidate) {
    return candidate.size();
  }

  public static void main(String[] args) {
    // Create an instance of DefaultTeam and Evaluation
    DefaultTeam team = new DefaultTeam();
    Evaluation eval = new Evaluation();

    // Prepare the list of points
    ArrayList<Point> points = new ArrayList<>();
    String filePath = "input.points"; // Path to the file

    // Read points from file
    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(" ");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            points.add(new Point(x, y));
        }
    } catch (IOException e) {
        System.err.println("Error reading file: " + e.getMessage());
        return;
    }

    // Define edgeThreshold (modify as needed)
    int edgeThreshold = 100;

    // Compute the FVS
    ArrayList<Point> fvs = team.calculFVS(points, edgeThreshold);

    // Validate the solution
    boolean isValid = eval.isValid(points, fvs, edgeThreshold);

    // Print results
    System.out.println("Computed Feedback Vertex Set (FVS):");
    for (Point p : fvs) {
        System.out.println(p);
    }
    System.out.println("Size of FVS: " + fvs.size());
    System.out.println("Is solution valid? " + (isValid ? "Yes" : "No"));
}

}
