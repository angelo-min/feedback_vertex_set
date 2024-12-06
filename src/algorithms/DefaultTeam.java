package algorithms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class Pair<T1, T2> {
    public final T1 first;
    public final T2 second;

    Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair<?,?>)) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (!Objects.equals(first, pair.first)) return false;
        return Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        return result;
    }
}

final class Point {
    public final int id;

    Point(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point point)) return false;
        return id == point.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}

public class DefaultTeam {
    // MAX_POPULATION=10 and MAX_NO_PROGRESS=2 gave 80.74 in 6 minutes
    // MAX_POPULATION=14 and MAX_NO_PROGRESS=3 gave 80.55 in 16 minutes on a weaker computer
    // MAX_POPULATION=20 and MAX_NO_PROGRESS=4 gave 80.46 in 30 minutes on a weaker computer
    // MAX_POPULATION=100 and MAX_NO_PROGRESS=5 gave 80.24 in 1 hour 30 minutes
    // MAX_POPULATION=200 and MAX_NO_PROGRESS=5 gave 80.21 in about 4 hours

    private static final int MAX_POPULATION = 100; //max number of solutions in the population
    private static final int MAX_NO_PROGRESS = 5;
    private static final int MAX_ITERATIONS = 2024; //max number of iterations

    private boolean[] edgeMap;
    private HashMap<java.awt.Point, Point> pointMap;
    private ArrayList<java.awt.Point> pointList;
    private Point[] simplePointArr;
    private int pointCount;
    private int pointCountShift;

    // optimized points set that contains a boolean array (since the amount of points is very limited)
    // has nexts and prevs array that induce a linked list that speeds up iteration for more sparse sets
    // for iteration heavy functions we try to use random access containers anyway
    private final class PointSet extends AbstractSet<Point> {
        private boolean[] points = new boolean[pointCount];
        private int[] nexts = new int[pointCount];
        private int[] prevs = new int[pointCount];
        private int first = -1;
        private int size = 0;
        private boolean cacheReset = true;
        private int hashCache = 0;

        public PointSet() {
            clear();
        }

        public PointSet(Collection<Point> collection) {
            if (collection instanceof PointSet pointSet) {
                size = pointSet.size;
                first = pointSet.first;
                System.arraycopy(pointSet.points, 0, points, 0, pointCount);
                System.arraycopy(pointSet.nexts, 0, nexts, 0, pointCount);
                System.arraycopy(pointSet.prevs, 0, prevs, 0, pointCount);
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
            return points[((Point) o).id];
        }

        public boolean containsId(int id) {
            return points[id];
        }

        @Override
        public Iterator<Point> iterator() {
            return new Iterator<>() {
                int curId = first;
                @Override
                public boolean hasNext() {
                    return curId != -1;
                }

                @Override
                public Point next() {
                    Point res = simplePointArr[curId];
                    curId = nexts[curId];
                    return res;
                }

                @Override
                public void remove() {
                    removeId(curId);
                }
            };
        }

        @Override
        public boolean add(Point point) {
            int id = point.id;
            boolean changed = !points[id];
            points[id] = true;
            if (changed) {
                cacheReset = true;
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
                cacheReset = true;
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
            return removeId(((Point) o).id);
        }

        @Override
        public void clear() {
            Arrays.fill(points, false);
            Arrays.fill(nexts, -1);
            Arrays.fill(prevs, -1);
            first = -1;
            size = 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PointSet pointSet)) return false;
            if (size != pointSet.size) return false;
            if (!cacheReset && !pointSet.cacheReset && hashCache != pointSet.hashCache) return false;

            return Arrays.equals(points, pointSet.points);
        }

        @Override
        public int hashCode() {
            if (cacheReset) {
                hashCache = Arrays.hashCode(points);
                cacheReset = false;
            }
            return hashCache;
        }
    }

    public ArrayList<java.awt.Point> calculFVS(ArrayList<java.awt.Point> _points, int edgeThreshold) {
        // we convert the input points into simple classes that just contain an id for the sake of speed
        pointCount = _points.size();
        pointCountShift = 32 - Integer.numberOfLeadingZeros(pointCount);
        pointMap = new HashMap<>();
        pointList = new ArrayList<>();

        // use shift instead of multiplication because the isEdge function is called a lot
        edgeMap = new boolean[_points.size() << pointCountShift];
        simplePointArr = new Point[_points.size()];
        for (java.awt.Point p: _points) {
            pointList.add(p);
            Point newPoint = new Point(pointList.size() - 1);
            pointMap.put(p, newPoint);
            simplePointArr[newPoint.id] = newPoint;
        }
        PointSet points = new PointSet();
        points.addAll(pointMap.values());

        for (Point p: points) {
            for (Point q: points) {
                edgeMap[(p.id << pointCountShift) + q.id] = pointList.get(p.id).distance(pointList.get(q.id)) < edgeThreshold;
            }
        }

        PriorityQueue<ArrayList<Point>> population = generateInitialPopulation(points, edgeThreshold, MAX_POPULATION);

        int no_improvement_counter = -1;

        ArrayList<Point> bestSolution = new ArrayList<>();
        for (int iter=0;iter<MAX_ITERATIONS;iter++){
            PriorityQueue<ArrayList<Point>> nextPopulation = new PriorityQueue<>(Comparator.comparingInt(this::score));
            nextPopulation.addAll(population);
            //keep only the best solutions
            while(nextPopulation.size() > MAX_POPULATION/2){
                ArrayList<Point> worstSolution = Collections.max(nextPopulation, Comparator.comparingInt(this::score));
                nextPopulation.remove(worstSolution);
            }

            //add new greedy solutions to the population
            while (nextPopulation.size() < MAX_POPULATION) {
                nextPopulation.add(localSearch(greedy(points, edgeThreshold), points, edgeThreshold));
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
            if(no_improvement_counter >= MAX_NO_PROGRESS){
                break;
            }
        }

        return new ArrayList<>(population.peek().stream().map(p -> pointList.get(p.id)).toList());
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
            population.add(localSearch(greedy(points, edgeThreshold), points, edgeThreshold));
        }
        return population;
    }

    private ArrayList<Point> greedy(PointSet points, int edgeThreshold) {
        PointSet pointsCopy = new PointSet(points); // Create a copy of points

        Random random = new Random();
        ArrayList<Point> result = new ArrayList<>();

        while (!isSolution(result, pointsCopy, edgeThreshold)) {
            HashMap<Point, Integer> degreeCache = new HashMap<>();
            top: while (true) {
                degreeCache.clear();
                for (Point p : pointsCopy) {
                    int res = degree(p, pointsCopy, edgeThreshold);
                    if (res <= 1) { // throw out leaf nodes, almost never happens
                        pointsCopy.remove(p);
                        continue top;
                    }
                    degreeCache.put(p, res);
                }
                break;
            }

            if (pointsCopy.isEmpty()) {
                System.err.println("Error: Points exhausted before FVS is valid.");
                break;
            }
            List<Point> sortedPoints = new ArrayList<>(pointsCopy);

            Collections.shuffle(sortedPoints);
            sortedPoints.sort((a, b) -> degreeCache.get(b) - degreeCache.get(a));
            //get the highest degree node 9/10 times and the second highest 1/10 times
            Point chosenOne = random.nextInt(10) == 0 && sortedPoints.size() > 1
                    ? sortedPoints.get(1)
                    : sortedPoints.get(0);
            result.add(chosenOne);
            pointsCopy.remove(chosenOne);
        }

        return result;
    }
    private ArrayList<Point> localSearch(ArrayList<Point> solution, PointSet points, int edgeThreshold) {
        ArrayList<Point> current = new ArrayList<>(solution);

        //System.out.println("LS. First sol: " + current.size());
        ArrayList<Point> next;
        while (true) {
            next = remove2add1(current, points, edgeThreshold);
            if (score(next) >= score(current)) break;
            current = next;
        }
        //while (true) {
        //    next = remove3add2(current, points, edgeThreshold);
        //    if (score(next) >= score(current)) break;
        //    current = next;
        //}

        //System.out.println("LS. Last sol: " + current.size());
        return current;
    }
    private ArrayList<Point> remove2add1(ArrayList<Point> candidate, PointSet points, int edgeThreshold) {
        ArrayList<Point> test = new ArrayList<>(candidate);
        Collections.shuffle(test);
        PointSet rest = new PointSet(points);
        test.forEach(rest::remove);
        AtomicBoolean done = new AtomicBoolean(false);
        Semaphore semaphore = new Semaphore(0);
        for (int i=0;i<test.size();i++) {
            Point p = test.get(i);
            PointSet solutionRest = new PointSet(rest);
            solutionRest.add(p);
            int finalI = i;

            ArrayList<Point>[] neighbors = new ArrayList[pointCount];
            for (Point q: solutionRest) {
                ArrayList<Point> qNeighbors = new ArrayList<>();
                for (Point r: solutionRest) {
                    if (q != r && isEdge(q, r, edgeThreshold)) {
                        qNeighbors.add(r);
                    }
                }
                neighbors[q.id] = qNeighbors;
            }

            Thread thread = new Thread(() -> {
                for (int j = finalI + 1; j < test.size(); j++) {
                    if (done.get()) break;
                    Point q = test.get(j);
                    solutionRest.add(q);

                    for (Point r : rest) {
                        solutionRest.remove(r);
                        if (isSolution(solutionRest, edgeThreshold, neighbors, q, r)) {
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
            });
            thread.start();
        }

        try {
            semaphore.acquire(test.size());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return test;
    }

    // i never got it to find any better solutions so we dont use it
    private ArrayList<Point> remove3add2(ArrayList<Point> candidate, PointSet points, int edgeThreshold) {
        ArrayList<Point> currentSolution = new ArrayList<>(candidate);
        PointSet rest = new PointSet(points);
        currentSolution.forEach(rest::remove);
        AtomicBoolean done = new AtomicBoolean(false);
        Point[] restArr = new Point[rest.size];
        {
            Iterator<Point> it = rest.iterator();
            for (int i = 0; i < rest.size; i++) {
                restArr[i] = it.next();
            }
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Semaphore semaphore = new Semaphore(0);

        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), currentSolution.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);

        // Timeout task
        executor.schedule(() -> {
            done.set(true);
            semaphore.release(threadCount);
        }, 1, TimeUnit.SECONDS);

        for (int t = 0; t < threadCount; t++) {
            threadPool.submit(() -> {
                Random random = new Random();
                PointSet currentRest = new PointSet(rest);
                while (!done.get()) {
                    if (candidate.size() < 5) break;
                    if (restArr.length < 5) break;
                    Point p1 = candidate.get(random.nextInt(candidate.size()));
                    currentRest.add(p1);
                    Point p2;
                    while ((p2 = candidate.get(random.nextInt(candidate.size()))) == p1) {}
                    currentRest.add(p2);
                    Point p3;
                    while ((p3 = candidate.get(random.nextInt(candidate.size()))) == p1 || p3 == p2) {}
                    currentRest.add(p3);


                    Point r1 = restArr[random.nextInt(restArr.length)];
                    currentRest.remove(r1);
                    Point r2;
                    while ((r2 = restArr[random.nextInt(restArr.length)]) == r1) {}
                    currentRest.remove(r2);

                    if (isSolution(currentRest, edgeThreshold, null, null, null)) {
                        if (!done.getAndSet(true)) break;
                        PointSet solution = new PointSet(points);
                        solution.removeAll(currentRest);
                        currentSolution.clear();
                        currentSolution.addAll(solution);
                        semaphore.release(threadCount);
                        break;
                    }
                    currentRest.add(r2);
                    currentRest.add(r1);
                    currentRest.remove(p3);
                    currentRest.remove(p2);
                    currentRest.remove(p1);
                }
                semaphore.release();
            });
        }

        try {
            semaphore.acquire(threadCount);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        threadPool.shutdownNow();
        executor.shutdownNow();
        return currentSolution;
    }
    private boolean isSolution(ArrayList<Point> candidate, PointSet pointsIn, int edgeThreshold) {
        PointSet rest = new PointSet(pointsIn);
        candidate.forEach(rest::remove);
        return isSolution(rest, edgeThreshold, null, null, null);
    }
    private boolean isSolution(PointSet rest, int edgeThreshold, ArrayList<Point>[] neighbors, Point q, Point r) {
        if (rest.isEmpty()) return true;
        Point[] restArray = new Point[rest.size]; // for quick iteration
        {
            Iterator<Point> setIt = rest.iterator();
            for (int i = 0; i < restArray.length; i++) {
                restArray[i] = setIt.next();
            }
        }
        PointSet notVisited = new PointSet(rest);
        ArrayDeque<Pair<Point, Point>> stack = new ArrayDeque<>();

        int curId = 0;

        while (!notVisited.isEmpty()) {
            while (!notVisited.containsId(curId)) curId++;
            stack.push(new Pair<>(null, simplePointArr[curId]));
            notVisited.removeId(curId);

            while (!stack.isEmpty()) {
                Pair<Point, Point> frame = stack.pop();
                Point parent = frame.first, current = frame.second;
                notVisited.remove(current);
                if (neighbors == null || current == q) {
                    for (Point other : restArray) {
                        if (other != current && other != parent && isEdge(current, other, edgeThreshold)) {
                            if (!notVisited.contains(other)) return false;
                            stack.push(new Pair<>(current, other));
                        }
                    }
                } else {
                    ArrayList<Point> curNeighbors = neighbors[current.id];
                    for (int i = 0, l = curNeighbors.size(); i < l; i++) {
                        Point other = curNeighbors.get(i);
                        if (other != r && other != parent) {
                            if (!notVisited.contains(other)) return false;
                            stack.push(new Pair<>(current, other));
                        }
                    }
                    if (q != parent && isEdge(current, q, edgeThreshold)) {
                        if (!notVisited.contains(q)) return false;
                        stack.push(new Pair<>(current, q));
                    }
                }
            }
        }

        return true;
    }
    private boolean isEdge(Point p, Point q, int edgeThreshold) {
        return edgeMap[(p.id << pointCountShift) + q.id];
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
