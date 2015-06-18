package com.johnbnelson.jingen;


/**
 * Oh yea, Java. Not providing a pair because it is "semantically null" is a
 * great idea. I definitely enjoy reimplementing this functionality every time!
 */
public class Edge implements Comparable<Edge>{
    public final int a, b;

    public Edge(int a, int b) {
        assert a < b;
        this.a = a;
        this.b = b;
    }

    public Edge transpose() {
        return new Edge(b, a);
    }


    /**
     * Technically, this is a bad implementation. I don't do checking for
     * objects belonging to the correct class or for null. But, neither
     * of these conditions are possible in my library. And, this is not
     * a general purpose class.
     */
    @Override
    public int compareTo(Edge that) {
        if(this.a != that.a) { return (this.a < that.a) ? -1 : 1; }
        if(this.b != that.b) { return (this.b < that.b) ? -1 : 1; }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Edge edge = (Edge) o;

        if (a != edge.a) return false;
        if (b != edge.b) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = a;
        result = 31 * result + b;
        return result;
    }
}
