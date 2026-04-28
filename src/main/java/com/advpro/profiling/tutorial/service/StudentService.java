package com.advpro.profiling.tutorial.service;

import com.advpro.profiling.tutorial.model.Student;
import com.advpro.profiling.tutorial.model.StudentCourse;
import com.advpro.profiling.tutorial.repository.StudentCourseRepository;
import com.advpro.profiling.tutorial.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author muhammad.khadafi
 */
@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentCourseRepository studentCourseRepository;

    @Transactional(readOnly = true)
    public List<StudentCourse> getAllStudentsWithCourses() {
        return studentCourseRepository.findAllWithStudentAndCourse();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findStudentWithHighestGpa() {
        return studentRepository.findFirstByOrderByGpaDesc();
    }

    @Transactional(readOnly = true)
    public String joinStudentNames() {
        return String.join(", ", studentRepository.findAllNames());
    }
}

