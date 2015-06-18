import random
from collections import defaultdict


# CP/PASTE FROM: http://blog.johnbnelson.com/be_polite_when_using_numba.html
try:
    from numba import jit, autojit
except ImportError:
    def identity_decorator(*args, **kwargs):
        if len(args) == 1 and hasattr(args[0], '__call__'):
            return args[0]  # Parentheses-less application
        else:
            def _f(f):  # Parameterized application
                return f
            return _f
    jit = identity_decorator
    autojit = identity_decorator


@autojit
def random_node(nodes, excluding):
    """
    :return: a random element from nodes that is not equal to `excluding`.
    """
    selected = excluding
    while selected == excluding:
        selected = random.choice(nodes)
    return selected


@autojit
def random_idx(n, excluding):
    """
    :return: a random number, i, in [0, n), except for i == excluding.
    """
    selected = excluding
    while selected == excluding:
        selected = random.randint(0, n-1)
    return selected


@autojit
def random_edge_as_indices(n):
    """
    :return: a tuple of (i, j) representing an edge in a network of [0, n)
             nodes. This tuple has the property of i < j, forall edges.
    """
    a = random.randint(0, n-1)
    b = random_idx(n, a)
    
    # The following constraint is useful. Consider a set of edges in a
    # undirected graph. In this case, (1, 2) is semantically equivalent to
    # (2, 1). But, from the perspective of a set, these tuples are unique.
    # Using the canonical form of smaller number first enforces the correct
    # semantics.
    return (a, b) if a < b else (b, a)  


@autojit
def random_proportional_selection(weights, max_weight):
    """
    Select an index using proportional selection.

    The weights variable defines the proportions. Counter to expectation,
    normalization is unnecessary. For example, sampled eye color frequencies
    work fine. This is a boon to performance in most cases. Prefer this
    algorithm over bisecting searches and linear walks.

    :param weights: a sequence of weights
    :param max_weight: the maximum value in the weights parameter
    :return: an index, i, indicative of the weight selected
    """
    ############################################################################
    #  See: Lipowski, Adam, and Dorota Lipowska. "Roulette-wheel selection
    #       via stochastic acceptance." Physica A: Statistical Mechanics and
    #       its Applications 391.6 (2012): 2193-2196.
    #
    #       http://www.sciencedirect.com/science/article/pii/S0378437111009010
    ############################################################################
    n = len(weights)
    
    while True:
        i = int(n * random.random())
        if random.random() < (weights[i] / float(max_weight)):
            return i


@autojit
def random_pair_from_list(nodes):
    """
    :return: A unique pair of randomly selected nodes in edge canonical form.
    """
    i, j = random_edge_as_indices(len(nodes))
    a, b = nodes[i], nodes[j]

    return (a, b) if a < b else (b, a)  # Canonical form.


@autojit
def random_pair_from_set(nodes):
    """
    :return: A unique pair of randomly selected nodes in edge canonical form.
    """
    return random_pair_from_list(list(nodes))


class JinGen:
    def __init__(self, r_0=0.0005, r_1=2., gamma=0.005, z_star=5, 
                 barrier_iters=1):
        self.i = 0
        self.r_0 = r_0
        self.r_1 = r_1
        self.gamma = gamma
        self.z_star = z_star
        self.barrier_iters = barrier_iters

    ############################################################################
    # All add and remove operations on edges must use _add_edge and _remove_
    # edge, respectively. They maintain the set of edges, as expected. They
    # also maintain the degrees and mutual meet factors (i.e. k * (k-1)) for
    # each node. And -- only when necessary -- they recompute the maximum degree
    # and maximum mutual meet factor for the graph.
    #
    # Both these functions require edges in canonical form. That is, the smaller
    # index must come first in the pair of indices representing the edge.
    ############################################################################

    def _add_edge(self, edge):
        assert edge[0] < edge[1]

        self.edges.add(edge)
        
        for a, b in [edge, (edge[1], edge[0])]:
            self.adj_list[a].append(b)
            self.degrees[a] += 1
            k = self.degrees[a]
            j = k * (k - 1)
            self.mutual_meet_factors[a] = j
            if k > self.max_degree:
                self.max_degree = k
                self.max_mutual_meet_factor = j
            
    def _remove_edge(self, edge):
        assert edge[0] < edge[1]

        self.edges.remove(edge)
        
        for a, b in [edge, (edge[1], edge[0])]:
            self.adj_list[a].remove(b)
            self.degrees[a] -= 1
            k = self.degrees[a]
            self.mutual_meet_factors[a] = k * (k - 1)
            if (k+1) == self.max_degree:
                j = max(self.degrees)
                self.max_degree = j
                self.max_mutual_meet_factor = j * (j-1)

    # From Jin et al (2001):
    #   If a pair meet and there is not already a connection between them, a new
    #   connection is established unless one of them already has z* connections,
    #   in which case nothing happens. In other words, z* forms a hard upper
    #   limit on the degree z of any vertex, beyond which no more edges can
    #   be added.
    def _connection_can_be_made(self, edge):
        if edge in self.edges:
            return False
        
        a, b = edge
        return self.degrees[a] < self.z_star and self.degrees[b] < self.z_star
    
    # From Jin et al (2001):
    #   At each time-step, we choose $n_p * r_0$ pairs of vertices
    #   uniformly at random from the network to meet. If a pair 
    #   meet who do not have a pre-existing connection, and if neither
    #   of them has the maximum number of z* connections then a new 
    #   connection is established between them.
    def _meet_randomly(self):
        n_meetings = int(round(self.num_pairs * self.r_0))
        
        for _ in range(n_meetings):
            edge = random_edge_as_indices(self.n)
            if self._connection_can_be_made(edge):
                self._add_edge(edge)
    
    # From Jin et al (2001):
    #   At each time-step, we choose $n_m * r_1$ vertices at random, with
    #   probabilities proportional to $z_i(z_i-1)$. For each vertex chosen
    #   we randomly choose one pair of its neighbors to meet, and establish 
    #   a new connection between them if they do not have a pre-existing
    #   connection and if neither of them already has the maximum number
    #   z* of connections.
    def _introduce_friends(self):
        num_mutual = int(0.5 * sum(j for j in self.mutual_meet_factors))
        n_introductions = int(round(num_mutual * self.r_1))
        
        for _ in range(n_introductions):
            ego = random_proportional_selection(self.mutual_meet_factors, 
                                                self.max_mutual_meet_factor)
            
            alters = self.adj_list[ego]
            
            # You can't introduce your friends if you only have 1 or none.
            # But, this property is true by the random proportional selection
            # algorithm. k * (k - 1) = 0 for k = 0 and k = 1. I don't have 
            # to check it in production code.
            assert len(alters) >= 2

            edge = random_pair_from_list(alters)
            if self._connection_can_be_made(edge):
                self._add_edge(edge)
    
    # From Jin et al (2001):
    #   At each time-step, we choose $n_e*\gamma$ vertices with probability
    #   proportional to $z_i$. For each vertex chosen we choose one of its 
    #   neighbors uniformly at random and delete the connection to that 
    #   neighbor.
    def _lose_touch(self):
        n_lost = int(round(len(self.edges) * self.gamma))
        for _ in range(n_lost):
            ego = random_proportional_selection(self.degrees, self.max_degree)
            alters = self.adj_list[ego]
            alter = alters[random.randint(0, len(alters)-1)]
            edge = (ego, alter) if ego < alter else (alter, ego)
            self._remove_edge(edge)

    def _setup(self, n):
        # This was refactored out of __call__ so I could experiment
        # and test _add and _remove without calling self(*). Really, this is
        # screaming for extraction and implementation as a small network
        # construction class. But, since this class is small and I don't plan
        # on adding alternative generators to this library, I'll keep _add
        # and _remove here, and avoid the extra redirection costs.
        self.n = n
        self.edges = set()
        self.adj_list = defaultdict(list)
        self.degrees = [0] * n
        self.max_degree = 0
        self.mutual_meet_factors = [0] * n
        self.max_mutual_meet_factor = 0
        
        self.num_pairs = int(n * (n - 1) / 2)

    def generate(self, n=250, iterations=30000, callback=None):
        self._setup(n)
        
        for i in range(iterations):
            self.i = i
            self._meet_randomly()
            self._introduce_friends()
            if i > self.barrier_iters: 
                self._lose_touch()

            if callback is not None:
                callback(self)
            
        return self.edges
