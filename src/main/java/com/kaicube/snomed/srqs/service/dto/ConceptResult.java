package com.kaicube.snomed.srqs.service.dto;

import java.util.ArrayList;
import java.util.List;

public class ConceptResult {

	private String id;
	private String fsn;
	private List<RelationshipResult> relationships;

	public ConceptResult(String id) {
		this.id = id;
	}

	public ConceptResult(String id, String fsn) {
		this.id = id;
		this.fsn = fsn;
		relationships = new ArrayList<>();
	}

	public String getId() {
		return id;
	}

	public String getFsn() {
		return fsn;
	}

	public List<RelationshipResult> getRelationships() {
		return relationships;
	}

	public void addRelationship(RelationshipResult relationshipResult) {
		relationships.add(relationshipResult);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ConceptResult that = (ConceptResult) o;

		return id.equals(that.id);

	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "ConceptResult{" +
				"id='" + id + '\'' +
				'}';
	}
}
