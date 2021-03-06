/**
 * 
 */
package checkers.inference2.jcrypt2;

import static com.esotericsoftware.minlog.Log.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference2.Constraint;
import checkers.inference2.InferenceChecker;
import checkers.inference2.MaximalTypingExtractor;
import checkers.inference2.Reference;
import checkers.inference2.ConstraintSolver.FailureStatus;
import checkers.inference2.Reference.*;
import checkers.util.AnnotationUtils;

/**
 * @author dongy6
 * 
 */
public class Jcrypt2TypingExtractor extends MaximalTypingExtractor {

	private InferenceChecker checker;
	private Map<String, Map<Integer, String[]>> convertedReferences;
	private Map<String, Constraint> conversions;
	//private Set<Reference> needTypeCastRefs;

	public Jcrypt2TypingExtractor(InferenceChecker c) {
		super(c);
		checker = c;
		conversions = new HashMap<>();
		convertedReferences = checker.getConvertedReferences();
		//needTypeCastRefs = checker.getNeedTypeCastRefs();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see checkers.inference2.AbstractTypingExtractor#extract()
	 */
	@Override
	public List<Constraint> extract() {
		Collection<Reference> references = checker.getAnnotatedReferences()
				.values();
		info(this.getClass().getSimpleName(),
				"Picking up the maximal qualifier for " + references.size()
						+ " variables...");
		Comparator<AnnotationMirror> comparator = checker.getComparator();
		for (Reference r : references) {
			AnnotationMirror[] annos = r.getAnnotations(checker).toArray(
					new AnnotationMirror[0]);
			if (annos.length != 0) {
				// sort
				Arrays.sort(annos, comparator);
				// get the maximal annotation
				Set<AnnotationMirror> maxAnnos = AnnotationUtils
						.createAnnotationSet();
				maxAnnos.add(annos[0]);
				r.setAnnotations(maxAnnos, checker);
			}
		}
		return typeCheck();
	}

	@Override
	public List<Constraint> typeCheck() {
		info(this.getClass().getSimpleName(),
				"Verifying the concrete typing...");
		Set<Constraint> constraints = checker.getConstraints();
		List<Constraint> errors = new ArrayList<>();
		// avoid AH -> RND for print();
		updateConstraints(constraints);
		for (Constraint c : constraints) {
			Reference left = c.getLeft();
			Reference right = c.getRight();
			Set<AnnotationMirror> leftAnnos = left.getAnnotations(checker);
			Set<AnnotationMirror> rightAnnos = right.getAnnotations(checker);
			if (!leftAnnos.isEmpty() && !rightAnnos.isEmpty()) {
				AnnotationMirror leftAnno = leftAnnos.iterator().next();
				AnnotationMirror rightAnno = rightAnnos.iterator().next();
				if (!checker.getQualifierHierarchy().isSubtype(leftAnno,
						rightAnno)
						&& checker.getFailureStatus(c) == FailureStatus.ERROR) {
					errors.add(c);
				}
			}
			if (!c.toString().contains("inference-tests/jcrypt/EncryptionSample.java")) {
				leftAnnos = left.getAnnotations(checker);
				rightAnnos = right.getAnnotations(checker);
				conversionCheck(c, left, right, leftAnnos, rightAnnos);
				//typeCastCheck(c, left, right, rightAnnos);
			}
		}
		info(this.getClass().getSimpleName(),
				"Finished verifying the concrete typing. " + errors.size()
						+ " error(s)");
		info(this.getClass().getSimpleName(),
				"Finished extracting type conversions. " + conversions.size()
						+ " conversion(s)");
		return errors;
	}

	private void updateConstraints(Set<Constraint> constraints) {
		// avoid AH -> RND for print();
		boolean hasUpdate;
		do {
			hasUpdate = false;
			for (Constraint c : constraints) {
				Reference left = c.getLeft();
				Reference right = c.getRight();
				Set<AnnotationMirror> leftAnnos = left.getAnnotations(checker);
				Set<AnnotationMirror> rightAnnos = right.getAnnotations(checker);
				if (!leftAnnos.isEmpty() && !rightAnnos.isEmpty()) {
					AnnotationMirror leftAnno = leftAnnos.iterator().next();
					AnnotationMirror rightAnno = rightAnnos.iterator().next();
					if (getSimpleName(rightAnno.toString()).equals("RND")
							&& leftAnno != ((Jcrypt2Checker) checker).RND) {
						Set<AnnotationMirror> annotations = AnnotationUtils.createAnnotationSet();
						annotations.add(leftAnno);
						if (right.getKind() == RefKind.METH_ADAPT) {
							Reference ref = ((MethodAdaptReference) right).getDeclRef();
							ref.setAnnotations(annotations, checker);
						} else if (right.getKind() == RefKind.FIELD_ADAPT) {
							Reference ref = ((FieldAdaptReference) right).getDeclRef();
							ref.setAnnotations(annotations, checker);
						} else {
							right.setAnnotations(annotations, checker);
						}
						hasUpdate = true;
					}
				}
			}
		} while (hasUpdate);
	}

//	private void typeCastCheck(Constraint c, Reference left,
//			Reference right, Set<AnnotationMirror> rightAnnos) {
//		if ((left.getKind() == RefKind.FIELD
//				|| left.getKind() == RefKind.FIELD_ADAPT)
//				&& right.getKind() == RefKind.LOCAL
//				&& !rightAnnos.isEmpty()) {
//			String javaType = right.getType().getUnderlyingType().toString();
//	    	if (javaType.equals("int") || javaType.equals("java.lang.String")) {
//	    		needTypeCastRefs.add(right);
//	    	}
//		}
//	}

	public void conversionCheck(Constraint c, Reference left, Reference right,
			Set<AnnotationMirror> leftAnnos, Set<AnnotationMirror> rightAnnos) {
		if (c.getPos() == 0 || left.getKind() == RefKind.METH_ADAPT
				|| (right.getKind() == RefKind.METH_ADAPT && leftAnnos.isEmpty()
				&& left.getKind() != RefKind.LITERAL)
				|| left.getKind() == RefKind.NULL
				|| right.getKind() == RefKind.NULL) return;
		if (leftAnnos.isEmpty()) {
			if (rightAnnos.isEmpty()) return;
			else {
				String rightCryptType = right.getCryptType() == null ?
						rightAnnos.iterator().next().toString()
						: right.getCryptType().toString();
				String[] con = new String[] {"CLEAR", getSimpleName(rightCryptType)};
				addConversion(c, left.getIdentifier(), con);
			}
		} else {
			AnnotationMirror leftAnno = leftAnnos.iterator().next();
			String leftCryptType = left.getCryptType() == null ?
					leftAnno.toString() : left.getCryptType().toString();
			if (rightAnnos.isEmpty()) {
				String[] con = new String[] {getSimpleName(leftCryptType), "CLEAR"};
				addConversion(c, left.getIdentifier(), con);
			} else {
				AnnotationMirror rightAnno = rightAnnos.iterator().next();
				String rightCryptType = right.getCryptType() == null ? rightAnno
						.toString() : right.getCryptType().toString();
				if (!leftCryptType.equals(rightCryptType)) {
					String[] con = new String[] {getSimpleName(leftCryptType),
							getSimpleName(rightCryptType)};
					String id = left.getIdentifier();
					if (left.getKind() == RefKind.FIELD_ADAPT || left.getKind() == RefKind.FIELD) {
						id = right.getIdentifier();
					}
					addConversion(c, id, con);
				}
			}
		}
	}

	private void addConversion(Constraint c, String refId, String[] con) {
		Map<Integer, String[]> cons = convertedReferences.get(refId);
		if (cons == null) {
			cons = new HashMap<>();
		}
		cons.put(c.getPos(), con);
		convertedReferences.put(refId, cons);
		if (conversions.put(c.getPos() + ":" + refId, c) == null) {
			System.out.println(c.toString());
			System.out.println(refId + ": " + con[0] + " => " + con[1]);
		}
	}
	
	private String getSimpleName(String type) {
		return type.substring(type.lastIndexOf('.') + 1);
	}

}
