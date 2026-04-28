# Module 07 - Profiling

## Repository Notes

- Upstream repository: `https://github.com/muhammad-khadafi/exercise-profiling`
- Working branches used in this submission:
  - `main` -> setup commit `0b4a4a8` (`[Setup] Configure local profiling environment and JMeter plans`)
  - `optimize` -> optimization commit `2acc9d3` (`[Refactoring] Optimize student query paths for profiled endpoints`)
- Because the automated environment could not drive the IntelliJ Profiler UI directly, JVM-level profiling evidence was collected with Java Flight Recorder (JFR). The hotspot findings are the same kind of runtime sampling data that IntelliJ Profiler builds on.

## Project Setup

1. Cloned the source code from the module repository.
2. Configured PostgreSQL connection in `application.properties` to a local dedicated instance on port `55432`.
3. Used the module allowance to reduce seeded students from `20_000` to `5_000` because the original seeding volume was too slow for this machine and would block the rest of the workflow.
4. Ran the app and verified the schema creation.
5. Seeded the data through:
   - `GET /seed-data-master`
   - `GET /seed-student-course`
6. Final seeded volume:
   - `students`: `5,000`
   - `courses`: `10`
   - `student_courses`: `10,000`

## JMeter Test Plans

The following plans were created with the same structure requested by the module:

- `test_plan_1.jmx` -> `GET /all-student`
- `test_plan_2_all_student_name.jmx` -> `GET /all-student-name`
- `test_plan_3_highest_gpa.jmx` -> `GET /highest-gpa`

Each plan uses:

- `10` users
- ramp-up `1` second
- `1` loop
- listeners:
  - `View Results Tree`
  - `View Results in Table`
  - `Summary Report`
  - `Graph Results`

## Baseline Measurements

Warm-up runs were executed before measurement so the first cold JVM run was not used as the reference point.

### Direct endpoint timing

| Endpoint | Before (s) | After (s) | Improvement |
| --- | ---: | ---: | ---: |
| `/all-student` | 20.197 | 0.194 | 99.04% |
| `/all-student-name` | 0.194 | 0.028 | 85.57% |
| `/highest-gpa` | 0.076 | 0.020 | 73.68% |

### JMeter average response time

| Endpoint | Before (ms) | After (ms) | Improvement |
| --- | ---: | ---: | ---: |
| `/all-student` | 38702.5 | 411.1 | 98.94% |
| `/all-student-name` | 454.7 | 75.5 | 83.40% |
| `/highest-gpa` | 186.3 | 71.9 | 61.41% |

All three endpoints exceed the required `20%` improvement threshold.

## Profiling Findings

### Before optimization

JFR execution samples on `/all-student` repeatedly entered:

- `com.advpro.profiling.tutorial.service.StudentService.getAllStudentsWithCourses`
- `com.advpro.profiling.tutorial.controller.StudentController.seedStudents`

The dominant issue in the service layer was the N+1 query pattern:

1. load all students
2. run one query per student to load student courses
3. rebuild the response list in Java

Other inefficiencies found by code inspection and timing:

- `/highest-gpa` loaded all students and scanned them in memory
- `/all-student-name` concatenated strings repeatedly in a loop

### After optimization

After the refactor, repeated `/all-student` profiling no longer showed `StudentService.getAllStudentsWithCourses` as the dominant app-level hotspot. The remaining visible work shifted mostly to `StudentCourse.toString()` and response rendering, which is expected because the endpoint still serializes a large string response.

## Refactoring Done

### 1. `/all-student`

Before:

- `studentRepository.findAll()`
- `studentCourseRepository.findByStudentId(student.getId())` for every student
- manual `StudentCourse` reconstruction

After:

- added `StudentCourseRepository.findAllWithStudentAndCourse()`
- used `join fetch` to load `StudentCourse`, `Student`, and `Course` in one query
- returned the query result directly

### 2. `/highest-gpa`

Before:

- fetched all students
- scanned GPA in Java

After:

- added `StudentRepository.findFirstByOrderByGpaDesc()`
- delegated max GPA selection to the database

### 3. `/all-student-name`

Before:

- fetched all student entities
- concatenated strings with `+=` in a loop

After:

- added `StudentRepository.findAllNames()`
- fetched only the `name` column
- used `String.join(", ", ...)`

## JMeter Result Screenshots

### Baseline

#### `/all-student`

![Baseline all-student](docs/screenshots/baseline-test-plan-1.png)

#### `/all-student-name`

![Baseline all-student-name](docs/screenshots/baseline-test-plan-2.png)

#### `/highest-gpa`

![Baseline highest-gpa](docs/screenshots/baseline-test-plan-3.png)

### After optimization

#### `/all-student`

![Optimized all-student](docs/screenshots/optimized-test-plan-1.png)

#### `/all-student-name`

![Optimized all-student-name](docs/screenshots/optimized-test-plan-2.png)

#### `/highest-gpa`

![Optimized highest-gpa](docs/screenshots/optimized-test-plan-3.png)

## Conclusion From JMeter

Yes, there is a clear improvement in JMeter measurements for all endpoints.

- `/all-student` improved the most because the main bottleneck was database access amplification from the N+1 query pattern.
- `/all-student-name` improved because the application now fetches only names and avoids repeated string reallocation.
- `/highest-gpa` improved because the database performs the ordering and limit more efficiently than loading all rows into Java.

## Reflection

### 1. What is the difference between performance testing with JMeter and profiling with IntelliJ Profiler?

JMeter measures the behavior that an external client sees under load: response time, throughput, error rate, and how the application behaves with concurrent users. Profiling focuses on what happens inside the JVM while the request is being processed: which methods consume CPU time, which parts allocate memory, and where the real bottlenecks are. JMeter tells me that a request is slow; profiling tells me why it is slow.

### 2. How does profiling help identify and understand weak points in the application?

Profiling narrows the search space. Instead of guessing, I can see which methods remain active during expensive requests. In this module, the profiler evidence pointed to `getAllStudentsWithCourses`, then the source code showed the N+1 query loop and unnecessary object reconstruction. That makes the optimization decision concrete and defensible.

### 3. Is IntelliJ Profiler effective for analyzing bottlenecks?

Yes. A sampling profiler is effective because it shows where execution time is actually spent during a real request path. Even without manually driving the IntelliJ UI here, the same JVM profiling data was enough to isolate the expensive service method and verify that the hotspot moved after refactoring.

### 4. What are the main challenges in performance testing and profiling, and how do you overcome them?

The main challenges are getting stable measurements, avoiding cold-start noise, and separating infrastructure issues from code issues. I handle them by warming up the JVM first, keeping the dataset fixed across before/after runs, using the same JMeter plans for comparison, and validating profiler results against the actual source code.

### 5. What are the main benefits of using IntelliJ Profiler for application profiling?

The main benefits are faster hotspot discovery, better visibility into runtime behavior, and easier comparison before and after refactoring. It shortens the feedback loop because it connects a slow request directly to the responsible code path.

### 6. How do you handle inconsistent results between profiling and JMeter?

I treat them as complementary signals, not contradictions. JMeter reflects end-to-end latency and concurrency effects, while profiling reflects internal execution cost. If they differ, I rerun the test after warm-up, check data volume, examine I/O or database effects, and verify whether the bottleneck is CPU-bound, query-bound, or serialization-bound.

### 7. What strategies do you implement after analyzing performance testing and profiling results, and how do you ensure functionality is preserved?

I change the smallest thing that removes the dominant cost first. In this module that meant pushing filtering/ordering work to the database, eliminating the N+1 query pattern, and reducing unnecessary object and string work. To preserve functionality, I kept the endpoint contracts unchanged, rebuilt the application, reran tests, and compared before/after HTTP responses plus JMeter results on the same dataset.
