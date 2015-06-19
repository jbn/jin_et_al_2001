package com.johnbnelson.jingen;

import java.util.*;

/**
 * This class is a direct port of a Python Library that I implemented.
 * It is undocumented because I have no desire to maintain documentation
 * in two places. The Python-to-Java mapping is basically one-to-one.
 * Read the Python documentation.
 */
public class JinGen {
    public final double r0, r1, gamma;
    public final int zStar, barrierIters;

    protected int iteration, n;
    protected int numPairs;
    protected Set<Edge> edges;
    protected Map<Integer, List<Integer>> adjList;

    protected int[] degrees;
    protected int maxDegree;

    protected int[] mutualMeetFactors;
    protected int maxMutualMeetFactors;

    private Random rng;


    /**
     * Note: Java's RNG is shit. I don't want to impose any dependencies. But,
     * it is highly recommended that you drop in a higher-quality RNG. For
     * example: http://cs.gmu.edu/~sean/research/mersenne/MersenneTwister.java
     */
    public JinGen(double r0, double r1, double gamma, int zStar,
                  int barrierIters, Random rng) {
        this.r0 = r0;
        this.r1 = r1;
        this.gamma = gamma;
        this.zStar = zStar;
        this.barrierIters = barrierIters;
        this.rng = rng;
    }

    public JinGen(double r0, double r1, double gamma, int zStar,
                  int barrierIters) {
        this(r0, r1, gamma, zStar, barrierIters, new Random());
    }

    public JinGen() {
        this(0.0005, 2.0, 0.005, 5, 1);
    }

    protected void addEdge(Edge edge) {
        edges.add(edge);

        updateEdgeCountsOnAdd(edge);
        updateEdgeCountsOnAdd(edge.transpose());
    }

    private void updateEdgeCountsOnAdd(Edge edge) {
        // Create the list for this edge's A node if it doesn't exist.
        List<Integer> friendsOfA = adjList.get(edge.a);
        if(friendsOfA == null) {
            friendsOfA = new ArrayList<Integer>();
            adjList.put(edge.a, friendsOfA);
        }

        friendsOfA.add(edge.b);

        int k = ++degrees[edge.a];
        int j = k * (k - 1);
        mutualMeetFactors[edge.a] = j;
        if(k > maxDegree) {
            maxDegree = k;
            maxMutualMeetFactors = j;
        }
    }

    protected void removeEdge(Edge edge) {
        edges.remove(edge);

        updateEdgeCountsOnRemove(edge);
        updateEdgeCountsOnRemove(edge.transpose());
    }

    protected int findMadDegree() {
        int maxFound = 0;
        for(int i=0; i < n; ++i) {
            int x = degrees[i];
            if(x > maxFound) {
                maxFound = x;
            }
        }
        return maxFound;
    }

    private void updateEdgeCountsOnRemove(Edge edge) {
        List<Integer> friendsOfA = adjList.get(edge.a);
        friendsOfA.remove(friendsOfA.indexOf(edge.b));

        int k = --degrees[edge.a];
        mutualMeetFactors[edge.a] = k * (k - 1);
        if(k + 1 == maxDegree) {
            maxDegree = findMadDegree();
            maxMutualMeetFactors = maxDegree * (maxDegree - 1);
        }
    }

    private boolean connectionCanBeMade(Edge edge) {
        if(edges.contains(edge)) {
            return false;
        }

        return degrees[edge.a] < zStar && degrees[edge.b] < zStar;
    }

    private int randomIdxExcluding(int excluding, int nElements) {
        int selected = excluding;
        while(selected == excluding) {
            selected = rng.nextInt(nElements);
        }
        return selected;
    }

    private Edge randomEdgeAsIndices(int nElements) {
        int a = rng.nextInt(nElements);
        int b = randomIdxExcluding(a, nElements);

        return (a < b) ? new Edge(a, b) : new Edge(b, a);
    }

    protected void meetRandomly() {
        int nMeetings = (int)Math.round(numPairs * r0);
        for(int i=0; i < nMeetings; ++i) {
            Edge edge = randomEdgeAsIndices(n);
            if(connectionCanBeMade(edge)) {
                addEdge(edge);
            }
        }
    }

    private int randomProportionalSelection(int[] weights, int maxWeight) {
        while(true) {
            int i = (int)(n * rng.nextDouble());
            if(rng.nextDouble() < (weights[i] / (double)maxWeight)) {
                return i;
            }
        }
    }

    private int sumOfMutualMeetFactors() {
        int accumulator = 0;
        for(int i=0; i < n; ++i) {
            accumulator += mutualMeetFactors[i];
        }
        return accumulator;
    }

    private Edge randomPairFromList(List<Integer> alters) {
        Edge idxs = randomEdgeAsIndices(alters.size());

        int a = alters.get(idxs.a);
        int b = alters.get(idxs.b);

        return (a < b) ? new Edge(a, b) : new Edge(b, a);
    }

    protected void introduceFriends() {
        int numMutual = (int)(0.5 * sumOfMutualMeetFactors());
        int nIntroductions = (int)Math.round(numMutual * r1);
        for(int i=0; i < nIntroductions; ++i) {
            int ego = randomProportionalSelection(
                mutualMeetFactors, maxMutualMeetFactors
            );
            List<Integer> alters = adjList.get(ego);

            assert alters.size() >= 2;

            Edge edge = randomPairFromList(alters);
            if(connectionCanBeMade(edge)) {
                addEdge(edge);
            }
        }
    }

    protected void loseTouch() {
        int nLost = (int)(Math.round(edges.size() * gamma));
        for(int i=0; i < nLost; ++i) {
            int ego = randomProportionalSelection(degrees, maxDegree);

            List<Integer> alters = adjList.get(ego);
            int alter = alters.get(rng.nextInt(alters.size()));

            removeEdge(
                (ego < alter) ? new Edge(ego, alter) : new Edge(alter, ego)
            );
        }
    }

    protected void prepareSimulation(int n) {
        this.n = n;
        edges = new TreeSet<Edge>();
        adjList = new TreeMap<Integer, List<Integer>>();
        degrees = new int[n];
        maxDegree = 0;
        mutualMeetFactors = new int[n];
        maxMutualMeetFactors = 0;

        numPairs = (n * (n-1)) / 2;

        // TODO: Find all conditions that can result in infinite loops!
        if((int)Math.round(numPairs * r0) == 0) {
            throw new RuntimeException(
                "Bad Config: Zero probability of random friendships forming!"
            );
        }
    }

    /**
     * Extend this class to create your own observer by overriding callback().
     * The JVM should hotspot this code out as an empty method. (I think.)
     */
    protected void callback() { }

    public Set<Edge> generate(int n, int iterations) {
        prepareSimulation(n);

        for(int i=0; i < iterations; ++i) {
            iteration = i;
            meetRandomly();
            introduceFriends();
            if(i > barrierIters) {
                loseTouch();
            }

            callback();
        }

        return edges;
    }

    public double clusteringCoefficient() {
        int triads = 0,
            linkSum = 0;
        for(int i=0; i < n; ++i) {
            for(int j=i+1; j < n; ++j) {
                for(int k=j+1; k < n; ++k) {
                    if(edges.contains(new Edge(i, k)) &&
                       edges.contains(new Edge(j, k)) &&
                       edges.contains(new Edge(i, j))) {
                        triads += 1;
                    }
                }
            }
            linkSum += (degrees[i] * (degrees[i] - 1)) / 2.0;
        }
        return (triads * 3.0) / linkSum;
    }

    /**
     * Running this program will output at dot file, suitable for neato ploting.
     * If you want to use the output as a edge list, skip all lines without
     * a prefixing tab, the parse the "\ti -- j;" lines.
     */
    public static void main(String[] args) {
        //double r0, double r1, double gamma, int zStar,
        //int barrierIters;
        //
        int n = 250, iters = 30000;
        JinGen jinGen = null;
        if(args.length == 0) {
            jinGen = new JinGen();
        } else if(args.length != 7) {
            System.err.println(
                "Usage: JinGen n iters r0 r1 gamma zStar barrierIters"
            );
            System.exit(1);
        } else {
            n = Integer.parseInt(args[0]);
            iters = Integer.parseInt(args[1]);

            jinGen = new JinGen(
                Double.parseDouble(args[2]),
                Double.parseDouble(args[3]),
                Double.parseDouble(args[4]),
                Integer.parseInt(args[5]),
                Integer.parseInt(args[6])
            );
        }


        System.out.println("graph {");
        for(Edge edge : jinGen.generate(n, iters)) {
           System.out.println("\t" + edge.a + " -- " + edge.b + ";");
        }
        System.out.println("}");
    }
}

