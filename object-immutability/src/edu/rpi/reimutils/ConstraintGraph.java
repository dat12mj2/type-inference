package edu.rpi.reimutils;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.io.File;

import soot.ArrayType;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.Type;
import checkers.inference.reim.quals.Readonly;
import checkers.inference.reim.quals.Polyread;
import edu.rpi.AnnotatedValue;
import edu.rpi.AnnotationUtils;
import edu.rpi.Constraint;
import edu.rpi.InferenceTransformer;
import edu.rpi.reim.ReimTransformer;

public abstract class ConstraintGraph {

	// Rep:
	protected Graph<AnnotatedValue,CfgSymbol> graph; 
	protected Map<AnnotatedValue, AnnotatedValue> nodeToRep;
	protected CompactGraph<AnnotatedValue> ptGraph;
	
	
	// Utilities:
	protected InferenceTransformer reimTransformer;
	protected LibraryUtilities libraryUtils;
	
	
	// Special array "annotation".
	private static AnnotatedValue ARRAY = new AnnotatedValue("Arrays.",null,AnnotatedValue.Kind.COMPONENT,null);
	
	private static AnnotatedValue SUBTYPE = new AnnotatedValue("Subtype.",null,AnnotatedValue.Kind.COMPONENT,null);
	
	static AnnotatedValue LIB = new AnnotatedValue("Lib.",null,AnnotatedValue.Kind.COMPONENT,null);
	
	// Rep invariant: graph nodes are AnnotatedValues of Kind different than METH_ADAPT or FIELD_ADAPT
	// Rep invariant: Set of graph nodes = set of values in nodeToRep
	// Rep invariant: set of keys in nodeToRep = set of original variables in program
	
	public ConstraintGraph(InferenceTransformer transformer) {
		reimTransformer = transformer;
		graph = new Graph<AnnotatedValue,CfgSymbol>();
		libraryUtils = new LibraryUtilities(this);
		nodeToRep = new HashMap<AnnotatedValue, AnnotatedValue>();
		
	}
	protected abstract void addFieldOpen(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation);
	protected abstract void addFieldClose(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation);
	protected abstract void addCallOpen(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation);
	protected abstract void addCallClose(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation);
	protected abstract void addLocal(AnnotatedValue left, AnnotatedValue right);
	
	private void processCallClose(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation,
			AnnotatedValue rightHandSide) {
		addCallClose(left,right,annotation);
		if (!isReadonly(rightHandSide))
			addCallOpen(right,left,annotation);
	}
	private void processCallOpen(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation,
			AnnotatedValue rightHandSide) {
		addCallOpen(left,right,annotation);
		if (!isReadonly(rightHandSide))
			addCallClose(right,left,annotation);
	}
	void processFieldClose(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation,
			AnnotatedValue rightHandSide) {
		addFieldClose(left,right,annotation);
		if (!isReadonly(rightHandSide))
			addFieldOpen(right,left,annotation);
	}
	void processFieldOpen(AnnotatedValue left, AnnotatedValue right, AnnotatedValue annotation,
			AnnotatedValue rightHandSide) {
		addFieldOpen(left,right,annotation);
		if (!isReadonly(rightHandSide))
			addFieldClose(right,left,annotation);
	}

	void processLocal(AnnotatedValue left, AnnotatedValue right) {
		addLocal(left,right);
		if (!isReadonly(right))
			addLocal(right,left);
	}
	
	private boolean skipConstraint(Constraint c) {
		boolean result = false;
		
		// kind = 0 is SUB constraint, kind = 1 is EQU constraint
		// TODO: have to refactor to get rid of magic numbers
		// Here, we skip EQU constraints (Wei needs them to handle arrays. We don't.)
		if (c.getKind() == 1) return true;
		
		// Ignore constraints on simple types:
		if (!(c.getLeft() instanceof AnnotatedValue.AdaptValue) && 
				!(c.getLeft().getType() instanceof RefLikeType)) {
			return true;			
		}
		if (!(c.getRight() instanceof AnnotatedValue.AdaptValue) && 
				!(c.getRight().getType() instanceof RefLikeType)) {
			return true;
		}
		
		if (libraryUtils.isLibraryAdaptConstraint(c,reimTransformer)) {
			return true;
		}
		
		return result;		
	}
	
	/*
	 * @param constraints set of Plain (or ReIm?) constraints
	 * @effects fills in graph g
	 * @modifies this.g
	 */	
	public void createNewGraph() {
		Set<Constraint> constraints = reimTransformer.getConstraints();
		for (Constraint c : constraints) {			
				
				if (skipConstraint(c)) continue;
							
				// Handle subtype constraints: 
				if ((c.getLeft().getKind() == AnnotatedValue.Kind.PARAMETER) && (c.getRight().getKind() == AnnotatedValue.Kind.PARAMETER) ||
						(c.getLeft().getKind() == AnnotatedValue.Kind.THIS) && (c.getRight().getKind() == AnnotatedValue.Kind.THIS) ) {
					processCallOpen(c.getLeft(),c.getRight(),SUBTYPE,c.getRight());
					continue;
				}
				else if ((c.getLeft().getKind() == AnnotatedValue.Kind.RETURN) && (c.getRight().getKind() == AnnotatedValue.Kind.RETURN)) {
					processCallClose(c.getLeft(),c.getRight(),SUBTYPE,c.getRight());
					continue;
				}
				// System.out.println("Handling constraint "+c);
				AnnotatedValue left = c.getLeft();
				AnnotatedValue right = c.getRight();
				if (left instanceof AnnotatedValue.AdaptValue) {
					// We have a field read or method return
					AnnotatedValue.AdaptValue leftAdapt = (AnnotatedValue.AdaptValue) left;
					AnnotatedValue lhs = leftAdapt.getDeclValue();
					AnnotatedValue context = leftAdapt.getContextValue();
					
					assert (lhs.getKind() != AnnotatedValue.Kind.METH_ADAPT && lhs.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);
					assert (right.getKind() != AnnotatedValue.Kind.METH_ADAPT && right.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);
					
					if (left instanceof AnnotatedValue.FieldAdaptValue) {
						// Handling arrays. If array read, context is Array. Same for array writes.
						if (lhs.getKind() == AnnotatedValue.Kind.COMPONENT)
							lhs = ARRAY;
						processFieldClose(context,right,lhs,right);
					}
					else {
						assert (left instanceof AnnotatedValue.MethodAdaptValue);
						processCallClose(lhs,right,context,right);
					}
				}
				else if (right instanceof AnnotatedValue.AdaptValue) {
					// We have a field write or method call
					AnnotatedValue.AdaptValue rightAdapt = (AnnotatedValue.AdaptValue) right;
					AnnotatedValue rhs = rightAdapt.getDeclValue();
					AnnotatedValue context = rightAdapt.getContextValue();
					
					assert (rhs.getKind() != AnnotatedValue.Kind.METH_ADAPT && rhs.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);
					assert (left.getKind() != AnnotatedValue.Kind.METH_ADAPT && left.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);

					if (right instanceof AnnotatedValue.FieldAdaptValue) {						
						if (rhs.getKind() == AnnotatedValue.Kind.COMPONENT) 
							rhs = ARRAY;
						processFieldOpen(left,context,rhs,right);
					}
					else {
						assert (right instanceof AnnotatedValue.MethodAdaptValue);
						processCallOpen(left,rhs,context,right);
					}					
				}
				else { // is Local
					assert (right.getKind() != AnnotatedValue.Kind.METH_ADAPT && right.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);
					assert (left.getKind() != AnnotatedValue.Kind.METH_ADAPT && left.getKind() != AnnotatedValue.Kind.FIELD_ADAPT);
					//System.out.println("=== Adding local edge? "+ left + " ---> "+right);
					processLocal(left,right);
				}
												
		}
		// creates constraints due to library calls
		libraryUtils.processLibraryCalls(graph);
	}
		
	protected abstract void printHeaderString(); 

	// effects: prints ptGraph if ptFlag is true, prints constraint graph otherwise
	public void printGraph() {
		Graph<AnnotatedValue,CfgSymbol> g = graph;
		//if (ptFlag) g = ptGraph; else g = graph;
		int numEdges = 0;
		printHeaderString(); 
		for (AnnotatedValue node : g.getNodes()) {
			List<Edge<AnnotatedValue,CfgSymbol>> edges = g.getEdgesFrom(node);
			for (Edge<AnnotatedValue,CfgSymbol> edge : edges) {
				System.out.println(numEdges++ +" "+edge.toString());
			}
		}
		
		System.out.println("Total numEdges: "+numEdges+"\n");
		/*
		for (AnnotatedValue node : nodeToRep.keySet()) {
			if (node.getKind() == AnnotatedValue.Kind.ALLOC)
				System.out.println("NODE: "+node);
		}
		System.out.println("Total num keys: "+nodeToRep.keySet().size()+"\n\n");
		*/
	}
	
	
	public void printPtGraph() {
		Graph<AnnotatedValue,CfgSymbol> normalGraph = new Graph<AnnotatedValue,CfgSymbol>();
		int numEdges = 0;
		System.out.println("Printing points-to graph:");
		for (AnnotatedValue n : nodeToRep.keySet()) {
			List<Edge<AnnotatedValue,CfgSymbol>> edges = ptGraph.getEdgesInto(n);
			for (Edge<AnnotatedValue,CfgSymbol> edge : edges) {
				normalGraph.addEdge(edge.getSource(),edge.getTarget(),edge.getLabel());
			}
		}
		
		for (AnnotatedValue node : normalGraph.getNodes()) {
			List<Edge<AnnotatedValue,CfgSymbol>> edges = normalGraph.getEdgesFrom(node);
			for (Edge<AnnotatedValue,CfgSymbol> edge : edges) {
				System.out.println(numEdges++ +" "+edge.toString());
			}
		}
		
		System.out.println("Total numEdges: "+numEdges+"\n");
		/*
		for (AnnotatedValue node : nodeToRep.keySet()) {
			if (node.getKind() == AnnotatedValue.Kind.ALLOC)
				System.out.println("NODE: "+node);
		}
		System.out.println("Total num keys: "+nodeToRep.keySet().size()+"\n\n");
		*/
	}
	
	/*
	 * @modifies graph
	 * @effects: computes transitive closure over CFG grammar embedded in g 
	 * @param: flag = true means we are doing Closure of calls; flag = false we are doing closure of fields
	 * 
	 */		
	public void closure() {
		// Initialize queue with original Local edges
		Queue<Edge<AnnotatedValue,CfgSymbol>> queue = new LinkedList<Edge<AnnotatedValue, CfgSymbol>>();
		Set<Edge<AnnotatedValue,CfgSymbol>> visitedEdges = new HashSet<Edge<AnnotatedValue, CfgSymbol>>();

		initialize(queue, visitedEdges);
		
		System.out.println("Queue.size: "+queue.size());
		
		dynamicClosure(queue, visitedEdges);
		
	}
	
	protected void initialize(Queue<Edge<AnnotatedValue, CfgSymbol>> queue,
			Set<Edge<AnnotatedValue, CfgSymbol>> visitedEdges) {
		for (AnnotatedValue node : graph.getNodes()) {
			List<Edge<AnnotatedValue,CfgSymbol>> edges = graph.getEdgesFrom(node);
			for (Edge<AnnotatedValue,CfgSymbol> edge : edges) {
				CfgSymbol theLabel = edge.getLabel();
				if (theLabel instanceof AtomicOpenParen) {
					for (Edge<AnnotatedValue,CfgSymbol> next : graph.getEdgesFrom(edge.getTarget())) {
						if (next.getLabel() == CfgSymbol.LOCAL) {
							if (!visitedEdges.contains(next)) {
								queue.add(next);
								visitedEdges.add(next);
							}
						}
					}
				}
			}			
		}
	}
	
	
	
	protected void dynamicClosure(Queue<Edge<AnnotatedValue, CfgSymbol>> queue,
			Set<Edge<AnnotatedValue, CfgSymbol>> visitedEdges) {
		int count=0;
		while (!queue.isEmpty()) {
			System.out.println("queue.size: "+queue.size());
			Edge<AnnotatedValue,CfgSymbol> curr = queue.remove();
			//System.out.println("Current popped edge: "+curr);
			// Invariant: curr is LOCAL
			assert (curr.getLabel() == CfgSymbol.LOCAL);
			for (Edge<AnnotatedValue,CfgSymbol> next : graph.getEdgesFrom(curr.getTarget())) {
				addTransitiveLocalEdge(curr,next,queue,visitedEdges);
			}
			/*
			for (Edge<AnnotatedValue,CfgSymbol> prev : graph.getEdgesInto(curr.getSource())) {
				addTransitiveLocalEdge(prev,curr,queue,visitedEdges);
			}
			*/
			
			
			for (Edge<AnnotatedValue,CfgSymbol> next : graph.getEdgesFrom(curr.getTarget())) {
				for (Edge<AnnotatedValue,CfgSymbol> prev : graph.getEdgesInto(curr.getSource())) {
					if (prev.getLabel().match(next.getLabel())) {
						if (prev.getSource().equals(next.getTarget())) continue;
						addTransitiveLocal(queue,visitedEdges,prev.getSource(),next.getTarget());
						//System.out.println("Added ci LOCAL ci EDGE from: "+prev+" and "+next);
						count++;
						//addLocalEdge(queue,visitedEdges,prev.getSource(),next.getTarget(),CfgSymbol.LOCAL);
					}
				}
			}
		}
		//System.out.println("Counted "+count+" first level ci ci edges.");
	}
	
	private boolean hasAtomicOpenPredecessor(AnnotatedValue source) {
		for (Edge<AnnotatedValue,CfgSymbol> prevprev : graph.getEdgesInto(source)) {
			if (prevprev.getLabel() instanceof AtomicOpenParen) {
				return true;
			}
		}
		return false;
	}
	
	private void addTransitiveLocal(Queue<Edge<AnnotatedValue, CfgSymbol>> queue,
			Set<Edge<AnnotatedValue, CfgSymbol>> visitedEdges,
			AnnotatedValue left, AnnotatedValue right) {
		if (graph.hasEdge(left,right,CfgSymbol.LOCAL)) return; // edge is already in the graph.
		for (Edge<AnnotatedValue,CfgSymbol> prev : graph.getEdgesInto(left)) {
			if (prev.getLabel() instanceof AtomicOpenParen) {
				addLocalEdge(queue,visitedEdges,left,right,CfgSymbol.LOCAL);
			}
			else if (prev.getLabel() == CfgSymbol.LOCAL) {
				if (hasAtomicOpenPredecessor(prev.getSource())) {
					addLocalEdge(queue,visitedEdges,prev.getSource(),right,CfgSymbol.LOCAL);
				}
			}
		}
		if (!skipAddEdge(left,right)) 
			graph.addEdge(left,right,CfgSymbol.LOCAL);
	}
		
	
	// Filter depends on kind of constraint graph
	abstract protected boolean skipAddEdge(AnnotatedValue left, AnnotatedValue right);
	
	
	// requires leftEdge.target == rightEdge.source
	// effects finds transitive edge left+right and adds it to queue and visited and graph g
	// modifies queue and visited
	protected void addTransitiveLocalEdge(Edge<AnnotatedValue,CfgSymbol> leftEdge, Edge<AnnotatedValue,CfgSymbol> rightEdge,
			Queue<Edge<AnnotatedValue,CfgSymbol>> queue, Set<Edge<AnnotatedValue,CfgSymbol>> visited) { 
		assert (leftEdge.getTarget().equals(rightEdge.getSource()));

		AnnotatedValue X = leftEdge.getSource();
		CfgSymbol label1 = leftEdge.getLabel();		
		AnnotatedValue Z = rightEdge.getTarget();
		CfgSymbol label2 = rightEdge.getLabel();
		CfgSymbol label = label1.concat(label2);
		// No need to add self-edge with LOCAL annotation		
		addLocalEdge(queue, visited, X, Z, label);
		// System.out.println("Added edge "+X.toString() + "--- "+label.toString()+" --->"+Z.toString());
	}

	// requires: all args except for label are non-null
	protected void addLocalEdge(Queue<Edge<AnnotatedValue, CfgSymbol>> queue,
			Set<Edge<AnnotatedValue, CfgSymbol>> visited,
			AnnotatedValue X, AnnotatedValue Z, CfgSymbol label) {
		
		if (label == null) return;
		if (X.equals(Z) && label == CfgSymbol.LOCAL) return;
		if (X.getEnclosingMethod() == null || Z.getEnclosingMethod() == null) return;
		
		if (skipAddEdge(X,Z)) return;
		// if (flag && !X.getEnclosingMethod().equals(Z.getEnclosingMethod())) return;
				
		Edge<AnnotatedValue,CfgSymbol> transitiveEdge = new Edge<AnnotatedValue, CfgSymbol>(X,Z,label);
		if (!visited.contains(transitiveEdge) && !graph.hasEdge(X,Z,label)) {
			visited.add(transitiveEdge);
			queue.add(transitiveEdge);
			graph.addEdge(X,Z,label);
			//System.out.println("Added transitive local edge: "+transitiveEdge.toString());
		}
	}
	
	public boolean isReadonly(AnnotatedValue v) {
		Set<Annotation> annos = v.getAnnotations(reimTransformer);
		// System.out.println("Set of annotations for: "+v.toString()+" is "+annos);
		if (v instanceof AnnotatedValue.AdaptValue) { // if AdaptValue, annos is empty
			AnnotatedValue.AdaptValue av = (AnnotatedValue.AdaptValue) v;  
			if (isReadonly(av.getDeclValue())) 
				return true;
			else if (isReadonly(av.getContextValue()) && 
					av.getDeclValue().getAnnotations(reimTransformer).contains(AnnotationUtils.fromClass(Polyread.class)) ) 
				return true;
			else
				return false;
		}
		if (annos.contains(AnnotationUtils.fromClass(Readonly.class))) 
			return true;
		else 
			return false;
	}
	
	public boolean isMutable(AnnotatedValue v) {
		assert !(v instanceof AnnotatedValue.AdaptValue);
		Set<Annotation> annos = v.getAnnotations(reimTransformer);
		// System.out.println("Set of annotations for: "+v.toString()+" is "+annos);
		if (annos.contains(AnnotationUtils.fromClass(Readonly.class))) 
			return false;
		else if (annos.contains(AnnotationUtils.fromClass(Polyread.class)))
			return false;
		else
			return true;
	}

	// modifies: graph
	// effects: tracks X -(_i-> Y -)_i-> Z edges and adds local edges from X to Z
	//          TO BE CALLED after collapsing nodes into SCC
	public void addLocalEdges() {
		for (AnnotatedValue node : graph.getNodes()) {
			List<Edge<AnnotatedValue,CfgSymbol>> nexts = graph.getEdgesFrom(node);
			List<Edge<AnnotatedValue,CfgSymbol>> prevs = graph.getEdgesInto(node);
			
			for (Edge<AnnotatedValue,CfgSymbol> next : nexts) {
				for (Edge<AnnotatedValue,CfgSymbol> prev : prevs) {
					AnnotatedValue X = prev.getSource();
					AnnotatedValue Z = next.getTarget();				
					if (prev.getLabel().match(next.getLabel())) {
						// System.out.println("Trying to add a new local edge: "+X+"---->"+Z);
						if (X.equals(Z)) continue;						
						if (skipAddEdge(X,Z)) continue;
						
						if (!graph.hasEdge(X,Z,CfgSymbol.LOCAL)) {
							graph.addEdge(X,Z,CfgSymbol.LOCAL);
							//System.out.println("Added a new local edge: "+X+"---->"+Z);
						}
					}
				}
			}
		}
	}
	
	// BEGINS Code Building the Pt graph
	
	//BEGIN TRY
	HashMap<AnnotatedValue,HashSet<AnnotatedValue>> tryPtGraph = new HashMap<AnnotatedValue, HashSet<AnnotatedValue>>();
	//END TRY
	
	public void buildPtGraph() {
		HashSet<AnnotatedValue> visited = new HashSet<AnnotatedValue>();
		ptGraph = new CompactGraph<AnnotatedValue>();
		
		int total=0;
		for (AnnotatedValue node : nodeToRep.keySet()) {
			//System.out.println("Current node, of kind: "+node+" node kind: "+node.getKind());
			if (node.getKind() == AnnotatedValue.Kind.ALLOC) {
				total++;
			}
		}
		
		int i=0;	
		for (AnnotatedValue node : nodeToRep.keySet()) {
			//System.out.println("Current node, of kind: "+node+" node kind: "+node.getKind());
			if (node.getKind() == AnnotatedValue.Kind.ALLOC) {
				//System.out.println("Current node: "+node);				
				AnnotatedValue n = nodeToRep.get(node);
				if (visited.contains(n)) continue; // already examined.
				visited.add(n);
				 
				System.out.println("Start propagate alloc value "+n+" number "+i++ +" out of"+total+" allocs...");
				
				Queue<Edge<AnnotatedValue,CfgSymbol>> queue = new LinkedList();
				// initialize queue
				for (Edge<AnnotatedValue,CfgSymbol> e : graph.getEdgesFrom(n)) {
					addEdgeToQueue(queue, e);
				}
				// propagate queue
				while (!queue.isEmpty()) {
					Edge<AnnotatedValue,CfgSymbol> e1 = queue.remove();
					for (Edge<AnnotatedValue,CfgSymbol> e2 : graph.getEdgesFrom(e1.getTarget())) {
						addTransitiveEdgeToQueue(queue, e1, e2);
					}
				}
			}
		}
	}
	
	abstract protected void addTransitiveEdgeToQueue(Queue<Edge<AnnotatedValue, CfgSymbol>> queue,
			Edge<AnnotatedValue, CfgSymbol> e1, Edge<AnnotatedValue, CfgSymbol> e2); 
	
	abstract protected void addEdgeToQueue(Queue<Edge<AnnotatedValue, CfgSymbol>> queue, Edge<AnnotatedValue, CfgSymbol> e); 
	
	
	public HashMap<AnnotatedValue,HashSet<AnnotatedValue>> getPtSets(ConstraintGraph field) {
		HashMap<AnnotatedValue,HashSet<AnnotatedValue>> varToPtSet = new HashMap<AnnotatedValue, HashSet<AnnotatedValue>>();
		// Map from each rep to the set of Allocs new_i such that nodeToRep(new_i) = rep; null if no allocs.
		HashMap<AnnotatedValue,HashSet<AnnotatedValue>> repToAllocs = new HashMap<AnnotatedValue, HashSet<AnnotatedValue>>();
		for (AnnotatedValue alloc : nodeToRep.keySet()) {
			if (alloc.getKind() != AnnotatedValue.Kind.ALLOC) continue;
			//System.out.println("Adding "+alloc+" to the rep set for "+nodeToRep.get(alloc));
			addToMap(repToAllocs,nodeToRep.get(alloc),alloc);
			// will add reflexive pt edge for each ALLOC. TODO: maybe remove.
			addToMap(varToPtSet,nodeToRep.get(alloc),alloc);
		}		
		for (AnnotatedValue var : nodeToRep.keySet()) {
			//System.out.println("var is "+var+ " and its rep is "+nodeToRep.get(var));
			for (Edge<AnnotatedValue,CfgSymbol> edge : ptGraph.getEdgesInto(nodeToRep.get(var))) {
				// This is the points to edge
				// System.out.println("----- HERE. Trying to add for "+var);
				for (AnnotatedValue alloc : repToAllocs.get(edge.getSource())) {	
					// System.out.println("----- HERE2. Trying to add for "+var+" the alloc: "+alloc);
					if (typeCompatible(alloc.getType(),var.getType())) {
						//System.out.println("----- Adding for "+var+" the alloc: "+alloc);
						addToMap(varToPtSet,var,alloc);
					}
				}
			}
			// In addition, we have to find all allocs in repToAlloc for the rep, and add them to pt set of var
			// if alloc,x,y,z all map to say x, then y and z aren't found to point to alloc.
			if (repToAllocs.get(nodeToRep.get(var)) != null) {
				for (AnnotatedValue alloc : repToAllocs.get(nodeToRep.get(var))) {
					if (typeCompatible(alloc.getType(),var.getType())) {
						//System.out.println("----- 2 Adding for "+var+" the alloc: "+alloc);
						addToMap(varToPtSet,var,alloc);
					}
				}
			}
		}
		HashMap<AnnotatedValue,HashSet<AnnotatedValue>> ptSets = new HashMap();
		intersectPtSets(field, varToPtSet, ptSets);
		return ptSets;
	}
	
	// effects: intersects this.ptGraph with field.ptGraph and stores result in ptSet hashMap
	private void intersectPtSets(ConstraintGraph field,
			HashMap<AnnotatedValue, HashSet<AnnotatedValue>> varToPtSet, HashMap<AnnotatedValue,HashSet<AnnotatedValue>> ptSets) {
		System.out.println(getClass()+"\n\n\n");
		long numpt = 0;
		long total = 0;
		
		String outputDir = SourceLocator.v().getOutputDir();
		String fileName = getClass().toString().substring(getClass().toString().lastIndexOf(".")+1);
		if (field != null) fileName = fileName+"_intersect"; 
		PrintStream ptDump = null;
		
		try {
			ptDump = new PrintStream(outputDir + File.separator + fileName);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (AnnotatedValue v : varToPtSet.keySet()) {
			//if (v.getType().toString().equals("java.lang.Object")) continue;
			ptDump.println("The pt set for "+v);
			total++;
			for (AnnotatedValue alloc : varToPtSet.get(v)) {
				if (field != null) {
					if ( field.ptGraph.hasEdge(field.nodeToRep.get(alloc), field.nodeToRep.get(v), CfgSymbol.LOCAL) ||
						     (field.nodeToRep.get(alloc) == field.nodeToRep.get(v))) {
						ptDump.println("---- "+alloc);
						numpt++;
						addToMap(ptSets,v,alloc);
					}
				}
				else {
					ptDump.println("---- "+alloc);		
					numpt++;
				}
			}
		}
		double avg = ((double) numpt)/((double) total);
		System.out.println("Avg pt set size: "+avg);
	}
	
	// modifies: map
	// effects: adds v to the set for key key; no effect if v already there
	static <T> void addToMap(HashMap<T,HashSet<T>> map, T key, T v) {
		HashSet<T> set = map.get(key);
		if (set == null) {
			set = new HashSet<T>();
			map.put(key,set);
		}
		set.add(v);
	}
	
	private boolean typeCompatible(Type alloc, Type var) {
		// System.out.println("Starting typeCompatible: "+alloc+" and "+var);
		if (alloc.equals(var)) {
			return true;
		}
		else if (var instanceof ArrayType) {
			// then alloc must be an array too
			if (alloc instanceof ArrayType) {
				return typeCompatible(((ArrayType) alloc).getElementType(), ((ArrayType) var).getElementType());
			}
			else {
				return false;
			}
		}
		else if (var instanceof RefType) {
			RefType ref = (RefType) var;
			if (ref.getSootClass().hasSuperclass() == false) {
				// this indicates that var is java.lang.Object
				return true;
			}
			else if (!(alloc instanceof RefType)) {
				// Can't have an array alloc when var is not an array	
				// System.out.println("HERE for "+alloc+" and "+var);
				return false;
			}
			else if (ref.getSootClass().isInterface()) {
				// var is an interface. 
				// TODO: Double check whether transitive interfaces are included into getInterfaces:
				// E.g., X implements I, where I extends J. Would J be into getInterfaces? Assume yes.
				SootClass allocClass = ((RefType) alloc).getSootClass();
				SootClass curr = allocClass;
				HashSet<SootClass> allInterfaces = new HashSet<SootClass>();
				while (curr.hasSuperclass()) {
					allInterfaces.addAll(curr.getInterfaces());
					curr = curr.getSuperclass();
				}				
				boolean result = allInterfaces.contains(ref.getSootClass());
				return result;
			}
			else { 
				// is class 
				return (alloc.merge(ref,Scene.v()).equals(ref));
			}
		}			
		else {
			// Shouldn't happen.
			return false;
		}
		
	}
	
	public HashMap<AnnotatedValue,AnnotatedValue> getNodeToRep() {
		return new HashMap(nodeToRep);
	}
	
}
