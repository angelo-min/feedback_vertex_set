package algorithms;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultTeam {

  private static final int MAX_POPULATION = 10; //max number of solutions in the population
  private static final int MAX_ITERATIONS = 2024; //max number of iterations

  public ArrayList<Point> calculFVS(ArrayList<Point> _points, int edgeThreshold) {
    HashSet<Point> points = new HashSet<>(_points);
    
    PriorityQueue<ArrayList<Point>> population = generateInitialPopulation(points, edgeThreshold, MAX_POPULATION);

    int no_improvement_counter = 0;

    ArrayList<Point> bestSolution = new ArrayList<>();
    for (int iter=0;iter<MAX_ITERATIONS;iter++){
      PriorityQueue<ArrayList<Point>> nextPopulation = new PriorityQueue<>(Comparator.comparingInt(this::score));

      while(!population.isEmpty()){
        ArrayList<Point> solution = population.poll();
        nextPopulation.add(localSearch(solution, points, edgeThreshold));
      }

      //add new greedy solutions to the population
      for (int i=0; i<MAX_POPULATION /2; i++){
        nextPopulation.add(greedy(points, edgeThreshold));
      }

      //keep only the best solutions
      while(nextPopulation.size() > MAX_POPULATION){
        ArrayList<Point> worstSolution = Collections.max(nextPopulation, Comparator.comparingInt(this::score));
        nextPopulation.remove(worstSolution);
      }

      population = nextPopulation;

      ArrayList<Point> olderSolution = bestSolution;
      bestSolution = population.peek();
      System.out.println("Current best solution in iteration: " + iter + " size: " + bestSolution.size());

      if(bestSolution.size() < olderSolution.size()){
        no_improvement_counter = 0;
      }else{
        no_improvement_counter++;
      }
      if(no_improvement_counter > 3){
        break;
      }
    }
    
    return population.peek();
    //return greedy(points,edgeThreshold);
  }

  private PriorityQueue<ArrayList<Point>> generateInitialPopulation(HashSet<Point> points, int edgeThreshold, int size){
    if (points == null || points.isEmpty()) {
      throw new IllegalArgumentException("Input points are null or empty.");
  }
  
    PriorityQueue<ArrayList<Point>> population = new PriorityQueue<>(Comparator.comparingInt(this::score));
    for (int i = 0; i < size; i++){
      ArrayList<Point> solution = greedy(points, edgeThreshold);
      if(solution.isEmpty()){
        System.out.println("Warning: Generated an empty solution during initial population generation");
      }
      population.add(greedy(points, edgeThreshold));
    }
    return population;
  }

private ArrayList<Point> greedy(HashSet<Point> points, int edgeThreshold) {
  HashSet<Point> pointsCopy = new HashSet<>(points); // Create a copy of points

  ArrayList<Point> result = new ArrayList<>();
  HashMap<Point, Integer> degreeCache = new HashMap<>();
  pointsCopy.forEach(p -> degreeCache.put(p, degree(p, pointsCopy, edgeThreshold)));
  Random random = new Random();
  
  while (!isSolution(result, pointsCopy, edgeThreshold)) {
    if (pointsCopy.isEmpty()) {
      System.err.println("Error: Points exhausted before FVS is valid.");
      break;
    }
    List<Point> sortedPoints = new ArrayList<>(pointsCopy);
    sortedPoints.sort((a, b) -> degreeCache.get(b) - degreeCache.get(a));
    //get the highest degree node 9/10 times and the second highest 1/10 times
    Point chosenOne = random.nextInt(10) == 0 && sortedPoints.size() > 1 
      ? sortedPoints.get(1) 
      : sortedPoints.get(0);
    result.add(chosenOne);
    pointsCopy.remove(chosenOne);
    for (Point p : pointsCopy) if (isEdge(chosenOne, p, edgeThreshold)) {
      degreeCache.put(p, degreeCache.get(p) - 1);
    }
    degreeCache.remove(chosenOne);
  }

  return result;
}
  private ArrayList<Point> localSearch(ArrayList<Point> solution, HashSet<Point> points, int edgeThreshold) {
    ArrayList<Point> current = new ArrayList<>(solution);
    int noImprovementCounter = 0;

    //System.out.println("LS. First sol: " + current.size());

    for (int iter = 0; iter < 10; iter ++){
      ArrayList<Point> next = remove2add1(current, points, edgeThreshold);
      if (score(next) < score(current)){
        current = next;
        noImprovementCounter = 0;
      }else{
        noImprovementCounter++;
      }
      if(noImprovementCounter > 3){
        break;
      }
    }
    //System.out.println("LS. Last sol: " + current.size());
    return current;

//  return current;
  }
  private ArrayList<Point> remove2add1(ArrayList<Point> candidate, HashSet<Point> points, int edgeThreshold) {
    ArrayList<Point> test = new ArrayList<>(candidate);
    long seed = System.nanoTime();
    Collections.shuffle(test, new Random(seed));
    HashSet<Point> rest = new HashSet<>(points);
    test.forEach(rest::remove);
    AtomicBoolean done = new AtomicBoolean(false);
    Semaphore semaphore = new Semaphore(0);

    for (int i=0;i<test.size();i++) {
      Point p = test.get(i);
      HashSet<Point> solutionRest = new HashSet<>(rest);
      solutionRest.add(p);
      int finalI = i;
      new Thread(() -> {
        for (int j = finalI + 1; j < test.size(); j++) {
          if (done.get()) break;
          Point q = test.get(j);
          solutionRest.add(q);

          for (Point r : rest) {
            solutionRest.remove(r);
            if (isSolution(new HashSet<>(solutionRest), edgeThreshold)) {
              if (done.getAndSet(true)) break;
              test.remove(j);
              test.remove(finalI);
              test.add(r);
              semaphore.release(test.size());
            }
            solutionRest.add(r);
          }

          solutionRest.remove(q);
        }
        semaphore.release();
      }).start();
    }

    try {
      semaphore.acquire(test.size());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return test;
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
    int degree=0;
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

    String filePath = "input.points"; // Path to the file

    RandomPointsGenerator randomPointsGenerator = new RandomPointsGenerator();

    int totalFVSsize = 0;
    int validSolutions = 0;

    for (int i =0; i<100; i++){
      randomPointsGenerator.generate(150);
      
      ArrayList<Point> points = new ArrayList<>();
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

      ArrayList<Point> fvs = team.calculFVS(points, edgeThreshold);

      boolean isValid = eval.isValid(points, fvs, edgeThreshold);

      // Print results
      System.out.println("Computed Feedback Vertex Set (FVS):");
      for (Point p : fvs) {
          System.out.println(p);
      }
      System.out.println("Size of FVS: " + fvs.size());
      System.out.println("Is solution valid? " + (isValid ? "Yes" : "No"));
      if(isValid){
        totalFVSsize += fvs.size();
        validSolutions++;
      }
      System.err.println("Average current size of FVS: " + (double)totalFVSsize/(i+1));
    }
    System.out.println("Average size of FVS: " + (double)totalFVSsize/100);
    System.out.println("Valid solutions: " + validSolutions + "/100");
    
}

}
