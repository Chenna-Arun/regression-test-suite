package com.testframework.regression.engine;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.service.TestCaseService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SuiteRegistry {

    private final TestCaseService testCaseService;

    public SuiteRegistry(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    // suiteId -> canonical test case names
    private static final Map<String, List<String>> SUITE_TO_NAMES = Map.of(
        "BLAZE_SMOKE", List.of(
            "BlazeDemo_HomePage_Test",
            "BlazeDemo_Dropdown_Test",
            "BlazeDemo_FlightSearch_Boston_London",
            "BlazeDemo_FlightSearch_NewYork_Paris",
            "BlazeDemo_ChooseFirstFlight",
            "BlazeDemo_PriceConsistency",
            "BlazeDemo_CompleteBooking_Valid",
            "BlazeDemo_Booking_EmptyFields",
            "BlazeDemo_Booking_InvalidCard",
            "BlazeDemo_EndToEnd_Flow"
        ),
        "REQRES_SMOKE", List.of(
            "ReqRes_GetUsers_Page2",
            "ReqRes_GetSingleUser_Valid",
            "ReqRes_GetSingleUser_NotFound",
            "ReqRes_CreateUser",
            "ReqRes_UpdateUser_PUT",
            "ReqRes_PatchUser",
            "ReqRes_DeleteUser",
            "ReqRes_Register_Valid",
            "ReqRes_Register_MissingPassword",
            "ReqRes_Login_Valid"
        ),
        // Combined suite: UI (10) + API (10) = 20 tests
        "COMBINED_SMOKE", List.of(
            // UI 6-15
            "BlazeDemo_HomePage_Test",
            "BlazeDemo_Dropdown_Test",
            "BlazeDemo_FlightSearch_Boston_London",
            "BlazeDemo_FlightSearch_NewYork_Paris",
            "BlazeDemo_ChooseFirstFlight",
            "BlazeDemo_PriceConsistency",
            "BlazeDemo_CompleteBooking_Valid",
            "BlazeDemo_Booking_EmptyFields",
            "BlazeDemo_Booking_InvalidCard",
            "BlazeDemo_EndToEnd_Flow",
            // API 26-35
            "ReqRes_GetUsers_Page2",
            "ReqRes_GetSingleUser_Valid",
            "ReqRes_GetSingleUser_NotFound",
            "ReqRes_CreateUser",
            "ReqRes_UpdateUser_PUT",
            "ReqRes_PatchUser",
            "ReqRes_DeleteUser",
            "ReqRes_Register_Valid",
            "ReqRes_Register_MissingPassword",
            "ReqRes_Login_Valid"
        )
    );

    public Optional<List<Long>> resolveSuiteToTestCaseIds(String suiteId) {
        if (suiteId == null) return Optional.empty();
        List<String> names = SUITE_TO_NAMES.getOrDefault(suiteId.toUpperCase(Locale.ROOT), Collections.emptyList());
        if (names.isEmpty()) return Optional.of(Collections.emptyList());

        List<TestCase> all = testCaseService.findAll();
        Map<String, TestCase> nameToCase = all.stream().collect(Collectors.toMap(TestCase::getName, tc -> tc, (a,b)->a));

        List<Long> ids = new ArrayList<>();
        for (String n : names) {
            TestCase tc = nameToCase.get(n);
            if (tc != null && tc.getId() != null) {
                ids.add(tc.getId());
            }
        }
        return Optional.of(ids);
    }
}





