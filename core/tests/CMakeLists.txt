# A scriptable fake worker lets the tests exercise lifecycle, timeouts, crashes and malformed
# output without a JVM.
add_executable(fake_worker fake_worker.cpp)
target_link_libraries(fake_worker PRIVATE nlohmann_json::nlohmann_json)
add_executable(fake_provider fake_provider.cpp)
target_link_libraries(fake_provider PRIVATE nlohmann_json::nlohmann_json)

add_executable(core_tests
  decision_test.cpp
  discovery_test.cpp
  orchestrator_test.cpp
  protocol_test.cpp
  provider_test.cpp
  review_test.cpp
  worker_client_test.cpp)
target_link_libraries(core_tests PRIVATE transpiler_core_lib GTest::gtest_main)
target_compile_definitions(core_tests PRIVATE FAKE_WORKER_PATH="$<TARGET_FILE:fake_worker>"
  FAKE_PROVIDER_PATH="$<TARGET_FILE:fake_provider>")
add_dependencies(core_tests fake_worker fake_provider)

include(GoogleTest)
gtest_discover_tests(core_tests DISCOVERY_TIMEOUT 30)
