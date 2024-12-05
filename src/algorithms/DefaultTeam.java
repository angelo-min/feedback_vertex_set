package algorithms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

record Pair<T1, T2>(T1 first, T2 second) {

}

record Point(int id) {

}

public class DefaultTeam {

  private static final int MAX_POPULATION = 10; //max number of solutions in the population
  private static final int MAX_ITERATIONS = 2024; //max number of iterations
  
  private boolean[][] edgeMap;
  private HashMap<java.awt.Point, Point> pointMap;
  private ArrayList<java.awt.Point> pointList;
  private Point[] simplePointArr;
  
  private class PointSet extends AbstractSet<Point> {
    private boolean[] points = new boolean[pointList.size()];
    private int[] nexts = new int[pointList.size()];
    private int[] prevs = new int[pointList.size()];
    private int first = -1;
    private int size = 0;

    public PointSet() {
      clear();
    }

    public PointSet(Collection<Point> collection) {
      if (collection instanceof PointSet pointSet) {
        size = pointSet.size;
        first = pointSet.first;
        System.arraycopy(pointSet.points, 0, points, 0, pointList.size());
        System.arraycopy(pointSet.nexts, 0, nexts, 0, pointList.size());
        System.arraycopy(pointSet.prevs, 0, prevs, 0, pointList.size());
      } else {
        clear();
        addAll(collection);
      }
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public boolean isEmpty() {
      return size == 0;
    }

    @Override
    public boolean contains(Object o) {
      return points[((Point) o).id()];
    }

    public boolean containsId(int id) {
      return points[id];
    }

    @Override
    public Iterator<Point> iterator() {
      return new Iterator<>() {
        int curId = -2;
        @Override
        public boolean hasNext() {
            return curId != -1 && (curId == -2 ? first : nexts[curId]) != -1;
        }

        @Override
        public Point next() {
          int nextCur;
          if (curId == -1 || (nextCur = (curId == -2 ? first : nexts[curId])) == -1) {
            throw new NoSuchElementException();
          }
          curId = nextCur;
          return simplePointArr[curId];
        }

        @Override
        public void remove() {
          removeId(curId);
        }
      };
    }

    @Override
    public boolean add(Point point) {
      int id = point.id();
      boolean changed = !points[id];
      points[id] = true;
      if (changed) {
        size++;
        if (first == -1) {
          first = id;
          return true;
        }
        if (first > id) {
          nexts[id] = first;
          prevs[first] = id;
          first = id;
          return true;
        }
        int prev = id-1;
        while (prev >= 0 && !points[prev]) {
          prev--;
        }
        int next = nexts[prev];
        nexts[prev] = id;
        prevs[id] = prev;
        nexts[id] = next;
        if (next != -1) {
          prevs[next] = id;
        }
      }
      return changed;
    }

    public boolean removeId(int id) {
      boolean changed = points[id];
      points[id] = false;
      if (changed) {
        size--;
        int prev = prevs[id], next = nexts[id];
        if (first == id) {
          first = next;
        }
        if (prev != -1) {
          nexts[prev] = nexts[id];
        }
        if (next != -1) {
          prevs[next] = prevs[id];
        }
      }
      return changed;
    }

    @Override
    public boolean remove(Object o) {
      return removeId(((Point) o).id());
    }

    @Override
    public void clear() {
      Arrays.fill(points, false);
      Arrays.fill(nexts, -1);
      Arrays.fill(prevs, -1);
      first = -1;
      size = 0;
    }
  }
  
  public ArrayList<java.awt.Point> calculFVS(ArrayList<java.awt.Point> _points, int edgeThreshold) {
    pointMap = new HashMap<>();
    pointList = new ArrayList<>();

    edgeMap = new boolean[_points.size()][_points.size()];
    simplePointArr = new Point[_points.size()];
    for (java.awt.Point p: _points) {
      pointList.add(p);
      Point newPoint = new Point(pointList.size() - 1);
      pointMap.put(p, newPoint);
      simplePointArr[newPoint.id()] = newPoint;
    }
    PointSet points = new PointSet();
    points.addAll(pointMap.values());

    for (Point p: points) {
      for (Point q: points) {
        edgeMap[p.id()][q.id()] = pointList.get(p.id()).distance(pointList.get(q.id())) < edgeThreshold;
      }
    }

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
      if(no_improvement_counter >= 2){
        break;
      }
    }
    
    return new ArrayList<>(population.peek().stream().map(p -> pointList.get(p.id())).toList());
    //return greedy(points,edgeThreshold);
  }

  private PriorityQueue<ArrayList<Point>> generateInitialPopulation(PointSet points, int edgeThreshold, int size){
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

private ArrayList<Point> greedy(PointSet points, int edgeThreshold) {
  PointSet pointsCopy = new PointSet(points); // Create a copy of points

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
  private ArrayList<Point> localSearch(ArrayList<Point> solution, PointSet points, int edgeThreshold) {
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
  private ArrayList<Point> remove2add1(ArrayList<Point> candidate, PointSet points, int edgeThreshold) {
    ArrayList<Point> test = new ArrayList<>(candidate);
    long seed = System.nanoTime();
    Collections.shuffle(test, new Random(seed));
    PointSet rest = new PointSet(points);
    test.forEach(rest::remove);
    AtomicBoolean done = new AtomicBoolean(false);
    Semaphore semaphore = new Semaphore(0);

    for (int i=0;i<test.size();i++) {
      Point p = test.get(i);
      PointSet solutionRest = new PointSet(rest);
      solutionRest.add(p);
      int finalI = i;
      new Thread(() -> {
        for (int j = finalI + 1; j < test.size(); j++) {
          if (done.get()) break;
          Point q = test.get(j);
          solutionRest.add(q);

          for (Point r : rest) {
            solutionRest.remove(r);
            if (isSolution(new PointSet(solutionRest), edgeThreshold)) {
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
  private boolean isSolution(ArrayList<Point> candidate, PointSet pointsIn, int edgeThreshold) {
    PointSet rest = new PointSet(pointsIn);
    candidate.forEach(rest::remove);
    return isSolution(rest, edgeThreshold);
  }
  private boolean isSolution(PointSet rest, int edgeThreshold) {
    return isSolution2(rest, edgeThreshold);
    // boolean r3 = new Evaluation().isValid(new ArrayList<>(rest), new ArrayList<>(), edgeThreshold);
    // boolean r2 = isSolution2(rest, edgeThreshold);
    // boolean r1 = isSolution1(rest, edgeThreshold);
    // if (r2 != r3) {
      // throw new RuntimeException("Error " + r1 + " " + r2 + " " + r3);
    // }
    // return r1;
  }
  private boolean isSolution2(PointSet rest, int edgeThreshold) {
    if (rest.isEmpty()) return true;

    PointSet notVisited = new PointSet(rest);
    ArrayDeque<Pair<Point, Point>> stack = new ArrayDeque<>();

    int curId = 0;

    while (!notVisited.isEmpty()) {
      while (!notVisited.containsId(curId)) curId++;
      stack.push(new Pair<>(null, simplePointArr[curId]));
      notVisited.removeId(curId);

      while (!stack.isEmpty()) {
        Pair<Point, Point> frame = stack.pop();
        Point parent = frame.first(), current = frame.second();
        notVisited.remove(current);
        for (Point other : rest) {
          if (other != current && other != parent && isEdge(current, other, edgeThreshold)) {
            if (!notVisited.contains(other)) return false;
            stack.push(new Pair<>(current, other));
          }
        }
      }
    }

    return true;
  }
  private boolean isSolution1(PointSet rest, int edgeThreshold) {
    ArrayList<Point> visited = new ArrayList<>();

    while (!rest.isEmpty()) {
      visited.clear();
      Iterator<Point> it = rest.iterator();
      visited.add(it.next());
      it.remove();
      for (int i=0;i<visited.size();i++) {
        for (Point p: rest) if (isEdge(visited.get(i),p,edgeThreshold)) {
          for (Point q: visited) {
            if (!q.equals(visited.get(i)) && isEdge(p, q, edgeThreshold))
              return false;
          }
          visited.add(p);
        }
      }
    }

    return true;
  }
  private boolean isEdge(Point p, Point q, int edgeThreshold) {
    return edgeMap[p.id()][q.id()];
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
      
      ArrayList<java.awt.Point> points = new ArrayList<>();
      // Read points from file
      try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(" ");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            points.add(new java.awt.Point(x, y));
        }
      } catch (IOException e) {
          System.err.println("Error reading file: " + e.getMessage());
          return;
      }

    // Define edgeThreshold (modify as needed)
      int edgeThreshold = 100;

      ArrayList<java.awt.Point> fvs = team.calculFVS(points, edgeThreshold);

      boolean isValid = eval.isValid(points, fvs, edgeThreshold);

      // Print results
      System.out.println("Computed Feedback Vertex Set (FVS):");
      for (java.awt.Point p : fvs) {
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
