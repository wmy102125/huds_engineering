package com.hugo.huds.diagnosticcase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity mapped to the existing {@code case_number_counters} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "case_number_counters")
public class CaseNumberCounter {

  @Id
  @Column(name = "case_year", nullable = false, updatable = false)
  private Integer caseYear;

  @Column(name = "last_no", nullable = false)
  private int lastNo;

  public CaseNumberCounter() {
    // Required by JPA.
  }

  public Integer getCaseYear() {
    return caseYear;
  }

  public void setCaseYear(Integer caseYear) {
    this.caseYear = caseYear;
  }

  public int getLastNo() {
    return lastNo;
  }

  public void setLastNo(int lastNo) {
    this.lastNo = lastNo;
  }

}
