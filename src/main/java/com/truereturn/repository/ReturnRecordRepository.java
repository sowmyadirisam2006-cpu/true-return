package com.truereturn.repository;

import com.truereturn.model.ReturnRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRecordRepository extends JpaRepository<ReturnRecord, Long> {
}