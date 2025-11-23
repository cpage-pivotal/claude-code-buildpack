package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example Spring Boot application demonstrating Claude Code integration.
 * <p>
 * This application automatically configures Claude Code CLI integration
 * and provides REST API endpoints for executing Claude commands.
 * </p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/claude/execute - Synchronous execution</li>
 *   <li>POST /api/claude/execute-async - Asynchronous execution</li>
 *   <li>POST /api/claude/stream - Server-Sent Events streaming</li>
 *   <li>GET /api/claude/health - Health check</li>
 *   <li>GET /demo/analyze - Example analysis endpoint</li>
 * </ul>
 *
 * @author Claude Code Buildpack Team
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
