## How to Run

### 1) Start the Spring Boot app
Run from the project folder that contains `pom.xml`:

```bash
C:\Users\DELL\Downloads\regression-test-suite\regression-test-suite> mvn spring-boot:run
```

The app runs at `http://localhost:8080`.

### 2) Import and run the Postman collection
File: `20 Test Cases - BlazeDemo UI + ReqRes API.postman_collection.json` (in the project root).

Steps:
- Open Postman → Import → Select the collection file above.
- Set collection/environment variable `baseUrl` to `http://localhost:8080`.
- In the collection, run in order:
  1. `1. Create UI Test Cases - BlazeDemo`
  2. `2. Create API Test Cases - ReqRes`
  3. `3. Execute All Tests in Parallel` → `Execute All 20 Tests - Parallel`

That’s it. The framework will run the 20 tests. Reports will be saved under `test-output/reports/`.

## Running the Combined Suite (UI + API Tests)(CURRENT COMMAND TO RUN THE PROJECT)

To execute the **Combined Suite** (which includes both UI and API test cases), use the following command:

```bash
.\mvnw.cmd clean test "-Dtest=tests.combined.CombinedSuiteRunner"
```
