package com.eaglepoint.console.api;

import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.api.middleware.ErrorHandler;
import com.eaglepoint.console.api.middleware.RateLimiter;
import com.eaglepoint.console.api.routes.*;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.repository.*;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.eaglepoint.console.service.*;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiServer {

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static Javalin app;

    public static void start(int port) throws Exception {
        // Build services
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        var ds = dbConfig.getDataSource();

        // Repositories
        var userRepo = new UserRepository(ds);
        var tokenRepo = new ApiTokenRepository(ds);
        var communityRepo = new CommunityRepository(ds);
        var geozoneRepo = new GeozoneRepository(ds);
        var serviceAreaRepo = new ServiceAreaRepository(ds);
        var pickupPointRepo = new PickupPointRepository(ds);
        var leaderRepo = new LeaderAssignmentRepository(ds);
        var evalRepo = new EvaluationRepository(ds);
        var reviewRepo = new ReviewRepository(ds);
        var appealRepo = new AppealRepository(ds);
        var bedBuildingRepo = new BedBuildingRepository(ds);
        var bedRoomRepo = new BedRoomRepository(ds);
        var bedRepo = new BedRepository(ds);
        var bedHistoryRepo = new BedStateHistoryRepository(ds);
        var routeImportRepo = new RouteImportRepository(ds);
        var kpiRepo = new KpiRepository(ds);
        var exportJobRepo = new ExportJobRepository(ds);
        var scheduledJobRepo = new ScheduledJobRepository(ds);
        var auditRepo = new AuditTrailRepository(ds);
        var systemLogRepo = new SystemLogRepository(ds);
        var updateHistoryRepo = new com.eaglepoint.console.repository.UpdateHistoryRepository(ds);

        // Services
        var auditService = new AuditService(auditRepo);
        var systemLogService = new com.eaglepoint.console.service.SystemLogService(systemLogRepo);
        var notificationService = NotificationService.getInstance();
        var tokenService = new com.eaglepoint.console.security.TokenService();
        var authService = new AuthService(userRepo, tokenRepo, tokenService);
        var userService = new UserService(userRepo, tokenRepo, SecurityConfig.getInstance());
        var communityService = new CommunityService(communityRepo, auditService);
        var geozoneService = new GeozoneService(geozoneRepo);
        var serviceAreaService = new ServiceAreaService(serviceAreaRepo, communityRepo, auditService);
        var pickupPointService = new PickupPointService(
            pickupPointRepo, communityRepo, geozoneRepo,
            SecurityConfig.getInstance(), auditService,
            notificationService, systemLogService);
        var leaderService = new LeaderAssignmentService(leaderRepo, userRepo, serviceAreaRepo, auditService);
        var evalService = new EvaluationService(evalRepo, auditService);
        var reviewService = new ReviewService(evalRepo, userRepo, auditService);
        var appealService = new AppealService(evalRepo, auditService);
        var bedService = new BedService(bedRepo, SecurityConfig.getInstance(), auditService);
        var routeImportService = new RouteImportService(routeImportRepo, notificationService, auditService);
        var kpiService = new KpiService(kpiRepo, auditService);
        var exportService = new ExportService(
            exportJobRepo,
            communityRepo, serviceAreaRepo, pickupPointRepo,
            bedRepo, bedBuildingRepo, bedRoomRepo,
            userRepo, kpiRepo, geozoneRepo);
        var consistencyService = new ConsistencyService(ds);
        var updateVerifier = com.eaglepoint.console.security.UpdateSignatureVerifier.load();
        var updateService = new UpdateService(
            updateVerifier, updateHistoryRepo, auditService, notificationService);
        var scheduledJobService = new ScheduledJobService(scheduledJobRepo, auditService);

        // Re-encrypt any plaintext markers left in the V2 seed so no row in
        // users.staff_id_encrypted is stored as plaintext at runtime.
        new com.eaglepoint.console.service.SeedEncryptionService(
            ds,
            new com.eaglepoint.console.security.EncryptionUtil(SecurityConfig.getInstance().getEncryptionKey())
        ).reencryptPlaintextStaffIds();

        // Middleware instances
        var rateLimiter = new RateLimiter();

        // Start JobScheduler (must be started before we wire routes that reference it).
        // start() is idempotent so repeated entry-point calls (HeadlessEntryPoint, App)
        // and test harness usage all behave predictably.
        var jobScheduler = JobScheduler.getInstance();
        jobScheduler.init(scheduledJobRepo, pickupPointService, exportService);
        jobScheduler.start();

        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                rule.allowHost("http://127.0.0.1");
            }));
        });

        // Global before-handlers
        var authMiddleware = new AuthMiddleware(authService);
        app.before("/api/*", authMiddleware.handle());

        app.before("/api/*", rateLimiter.handle());

        // Register error handlers
        ErrorHandler.register(app);

        // Register all route groups
        AuthRoutes.register(app, authService);
        UserRoutes.register(app, userService);
        CommunityRoutes.register(app, communityService);
        GeozoneRoutes.register(app, geozoneService);
        ServiceAreaRoutes.register(app, serviceAreaService);
        PickupPointRoutes.register(app, pickupPointService);
        LeaderAssignmentRoutes.register(app, leaderService);
        EvaluationRoutes.register(app, evalService, reviewService, appealService);
        BedRoutes.register(app, bedService);
        RouteImportRoutes.register(app, routeImportService);
        KpiRoutes.register(app, kpiService);
        ExportRoutes.register(app, exportService);
        UpdateRoutes.register(app, updateService);
        SystemRoutes.register(app, auditRepo, systemLogRepo, scheduledJobRepo, jobScheduler, scheduledJobService);

        String bindHost = System.getProperty("api.bind", System.getenv().getOrDefault("API_BIND", "127.0.0.1"));
        app.start(bindHost, port);
        log.info("API server started on {}:{}", bindHost, port);

        // Resume incomplete jobs on startup
        exportService.resumeIncompleteJobs();
        routeImportService.resumeIncompleteImports();
    }

    public static void stop() {
        if (app != null) {
            app.stop();
            log.info("API server stopped");
        }
    }
}
