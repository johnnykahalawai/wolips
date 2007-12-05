package org.objectstyle.wolips.eomodeler.core.model;

import java.util.Set;
import java.util.TreeSet;

public class PropertyListSet<T> extends TreeSet<T> {
	public PropertyListSet() {
		super(PropertyListComparator.AscendingPropertyListComparator);
	}

	public PropertyListSet(Object[] guideArray) {
		super(PropertyListComparator.propertyListComparatorWithGuideArray(guideArray));
	}

	public PropertyListSet(Set<T> _set) {
		this();
		addAll(_set);
	}
}