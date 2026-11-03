package com.hugo.huds.diagnosticcase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 *  * Gives out case numbers of the form YYYY-NNNN (for example 2026-0001).
 *  *
 *  * <p>One counter row per year lives in case_number_counters. The single INSERT ... ON CONFLICT
 *  * statement creates the row for a new year or adds one to it, and returns the new value. The row is
 *  * locked until the surrounding transaction ends, so two cases opened at the same moment never get
 *  * the same number. If that transaction is rolled back, the counter is rolled back with it and no
 *  * number is lost.</p>
 *
 * @author Meiyan Wang
 * @date 2026-10-28 7:00 p.m.
 */
@Service
public class CaseNumberService {
    private static final String NEXT_NUMBER_SQL = """
      INSERT INTO case_number_counters (case_year, last_no)
      VALUES (?, 1)
      ON CONFLICT (case_year) DO UPDATE SET last_no = case_number_counters.last_no + 1
      RETURNING last_no
      """;

    private final JdbcTemplate jdbcTemplate;
    /** The year of a case number is the year in the shop's time zone, not in UTC. */
    private final ZoneId businessZone;

    public CaseNumberService(JdbcTemplate jdbcTemplate,
                             @Value("${huds.business.time-zone:America/Halifax}") String businessTimeZone) {
        this.jdbcTemplate = jdbcTemplate;
        // An unknown zone name fails here, when the application starts, not when the first case is opened.
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    /**
     * Returns the next case number. Must be called inside the transaction that saves the case,
     * otherwise the counter would move even when the case is not saved.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCaseNO(){
        int year = LocalDate.now(businessZone).getYear();
        Integer number = jdbcTemplate.queryForObject(NEXT_NUMBER_SQL,Integer.class,year);
        if (number == null) {
            throw new IllegalStateException("The case number counter returned nothing.");
        }
        return String.format("%d-%04d", year, number);
    }

}
