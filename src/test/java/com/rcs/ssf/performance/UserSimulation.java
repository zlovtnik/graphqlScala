package com.rcs.ssf.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class UserSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl("https://localhost:8443")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final Iterator<Map<String, Object>> feeder = 
        Stream.iterate(1, i -> i + 1)
            .limit(10000)
            .map(i -> {
                Map<String, Object> map = new HashMap<>();
                map.put("username", "user" + i);
                map.put("email", "user" + i + "@example.com");
                map.put("password", "password" + i);
                return map;
            })
            .collect(Collectors.toList())
            .iterator();

    private final ScenarioBuilder createUserScenario = scenario("Create User")
            .feed(feeder)
            .exec(http("Create User Request")
                    .post("/api/users")
                    .body(StringBody("""
                        {
                            "username": "#{username}",
                            "email": "#{email}",
                            "password": "#{password}"
                        }
                    """))
                    .asJson()
                    .check(status().is(200))
                    .check(jsonPath("$.id").saveAs("userId")))
            .pause(1)
            .exec(http("Get User Request")
                    .get("/api/users/#{userId}")
                    .check(status().is(200))
                    .check(jsonPath("$.username").is("#{username}")));

    public UserSimulation() {
        setUp(
            createUserScenario.injectOpen(
                rampUsers(5000).during(180), // Ramp up to 5000 users over 3 minutes
                constantUsersPerSec(50).during(120) // Maintain 50 users/sec for 2 minutes
            )
        ).protocols(httpProtocol)
         .assertions(
            global().responseTime().percentile3().lt(1000), // 95% of requests within 1 second
            global().responseTime().max().lt(5000),         // Max response time 5 seconds
            global().successfulRequests().percent().gt(95.0)  // 95% success rate
         );
    }
}
