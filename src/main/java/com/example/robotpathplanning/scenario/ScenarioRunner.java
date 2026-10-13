package com.example.robotpathplanning.scenario;

import com.example.robotpathplanning.fuzzy.AttentionLevel;
import com.example.robotpathplanning.fuzzy.MamdaniFuzzyInferenceSystem;
import com.example.robotpathplanning.model.*;
import com.example.robotpathplanning.obstacle.ObstacleManager;
import com.example.robotpathplanning.planning.AStarPlanner;
import com.example.robotpathplanning.planning.DStarLitePlanner;
import com.example.robotpathplanning.sensor.LidarSensor;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.example.robotpathplanning.fuzzy.AttentionLevel.Action.*;
import static com.example.robotpathplanning.scenario.ScenarioConfig.*;

/**
 * 4 ssenarini ardıcıl işlədən simulyasiya.
 *
 *   1. Maneəsiz yol: robot qutunu götürüb hədəfə aparır, operatoru fuzzy sistem izləyir.
 *   2. Qəfil maneələr: yolda forkliftlər və gedib-gələn robot çıxır, robot yolu yenidən planlaşdırır.
 *   3. LiDAR: robot xəritəni tədricən kəşf edir, eyni maneələr yenə çıxır.
 *   4. Bağlı yol: divarlar hədəfə gedən bütün yolları bağlayır, robot bunu LiDAR ilə
 *      kəşf edib qutunu geri qaytarır (yol olmadığının "sübutu").
 *
 * İş prinsipi: hər {@link ScenarioConfig#STEP_INTERVAL_MS} ms-də bir "tick" olur. Hər tick-də
 * robot ya bir xana irəli gedir, ya da gözləyir/yolu dəyişir, sonra vəziyyət
 * {@code publisher}-ə (WebSocket-ə) göndərilir. Bütün iş bir axında (thread) gedir,
 * ona görə sinxronizasiyaya ehtiyac yoxdur.
 */
public class ScenarioRunner {

    private static final int STATIC_WAIT_TICKS = 3;
    private static final int FACEOFF_MIN_TICKS = Math.max(1, 1000 / STEP_INTERVAL_MS);
    private static final int FACEOFF_MAX_TICKS = Math.max(FACEOFF_MIN_TICKS, 2000 / STEP_INTERVAL_MS);
    private static final int FACEOFF_COOLDOWN_TICKS = Math.max(1, 1000 / STEP_INTERVAL_MS);
    private static final double OPERATOR_BASELINE = 15;     // sakit operatorun ifadə/hərəkət dəyəri
    private static final double OPERATOR_PULL = 0.15;       // hər tick bazaya doğru qayıtma payı
    private static final int MAX_OPERATOR_STOP_TICKS = 3;   // operator səbəbi ilə ardıcıl ən çox neçə tick gözləmək olar
    /**
     * İki robot üz-üzə gələndə ekranda göstərilən robotlararası (V2V) əlaqə addımları. Real anbar
     * robotları "söhbət" etmir: dayanır, bir-birini tanıyır və kimin keçəcəyini razılaşdırır.
     */
    private static final String[] V2V_LINES = {
            "📡 V2V: qarşıda robot aşkarlandı — hər ikisi dayandı",
            "📡 V2V: yol hüququ razılaşdırılır...",
            "📡 V2V: yüklü robot prioritetdir — sarı robot yol verir"
    };

    private final Consumer<Map<String, Object>> publisher;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AStarPlanner astar = new AStarPlanner();
    private final MamdaniFuzzyInferenceSystem fis = new MamdaniFuzzyInferenceSystem();
    private final Random random = new Random();
    private ScheduledFuture<?> loop;

    // ---- Cari ssenarinin vəziyyəti (reset() ilə sıfırlanır) ----
    private int scenario;
    private GridMap world;
    private ObstacleManager obstacles;
    private LidarSensor lidar;               // null = LiDAR yoxdur, robot bütün xəritəni bilir
    private List<Cell> path;                 // robotun hazırda izlədiyi yol
    private int robotIndex;                  // robot path-in neçənci xanasındadır
    private int totalSteps;
    private final List<Cell> trail = new ArrayList<>();
    private boolean carrying;
    private int boxCount;
    private String status = "";
    private String fuzzyInfo = "";
    private boolean fuzzyAlert;
    private double simFacial, simMotion;     // simulyasiya olunan operator (bazaya qayıdan təsadüfi gəzinti)
    private int operatorStopTicks;           // operator STOP dediyi üçün ardıcıl gözlənilən tick-lər

    // ---- Ssenariyə xas bayraqlar ----
    private boolean pickedUp;                // qutu götürülüb
    private boolean returning;               // ssenari 4: qutu geri aparılır
    private boolean cautious;                // ssenari 3: "yavaşla" = hər ikinci tick-də gözlə
    private List<Cell> pendingObstacles;     // hələ çıxmamış qəfil maneələr
    private int cleanSteps;                  // maneəsiz yolun uzunluğu (müqayisə üçün)
    private int waitTicks;                   // statik maneədən sonra gözləmə
    private final Set<Cell> stopAtObstacles = new HashSet<>(); // üz-üzə gələndə qarşısında dayanılacaq maneələr
    private int faceoffLeft;                 // V2V dayanmasının qalan tick-ləri, -1 = dayanma yoxdur
    private int faceoffTotal;
    private int faceoffCooldown;             // dayanmadan sonra dərhal yenisi başlamasın
    private boolean movingRobotShownLastFrame; // hərəkət edən robot brauzerə göndərilən sonuncu kadrda görünürdümü

    // ---- D* Lite (ssenari 2, 3 və 4) ----
    private DStarLitePlanner dstar;          // null = bu ssenaridə A* işləyir
    private boolean planWithLidar;           // ssenari 3-4: planlaşdırıcı yalnız LiDAR-ın gördüyünü bilir
    private final Set<Cell> changedCells = new HashSet<>(); // D* Lite-a hələ bildirilməmiş dəyişən xanalar
    private int lastDstarExpanded, lastAstarExpanded;       // son planlaşdırmada açılan xanalar
    // İlk plan (yeni məqsəd) və artımlı yenilənmələr ayrı sayılır: D* Lite ilk planda A*-dan çox,
    // yenilənmələrdə isə az iş görür — üstünlüyü məhz ikincidədir
    private int initDstarExpanded, initAstarExpanded, initCount;
    private int updateDstarExpanded, updateAstarExpanded, updateCount;
    private String lastPlannerEvent;         // D* Lite-ın son hadisəsi (panelde göstərilir)

    public ScenarioRunner(Consumer<Map<String, Object>> publisher) {
        this.publisher = publisher;
    }

    public void start() {
        executor.execute(safe(this::scenario1));
    }

    public void stop() {
        executor.shutdownNow();
    }

    // ==================== SSENARİ 1: maneəsiz yol ====================

    private void scenario1() {
        reset(1);
        path = astar.planPath(world, START, BOX_CELL);
        show("Robot qutuya gedir...");
        loop(() -> {
            if (atEnd()) {
                endLoop();
                pickUp();
                show(BOX_CARRY + " QUTU GÖTÜRÜLDÜ! " + (BOX_TOTAL - BOX_CARRY) + " qutu qaldı.");
                later(1000, this::scenario1Delivery);
            } else {
                stepIfOperatorAllows("Robot qutuya gedir...");
            }
        });
    }

    private void scenario1Delivery() {
        List<Cell> cleanPath = astar.planPath(world, robotCell(), GOAL);
        if (!replan(cleanPath)) {
            show("Hədəfə yol tapılmadı!");
            return;
        }
        loop(() -> {
            if (atEnd()) {
                endLoop();
                dropBox();
                show("SSENARİ 1 TAMAM! Qutu çatdırıldı! (" + (cleanPath.size() - 1) + " addım). Maneələr əlavə olunur...");
                later(2000, this::scenario2);
            } else {
                stepIfOperatorAllows("Robot qutu daşıyır...");
            }
        });
    }

    // ==================== SSENARİ 2: qəfil maneələr ====================

    private void scenario2() {
        reset(2);
        List<Cell> cleanPath = astar.planPath(world, BOX_CELL, GOAL);
        cleanSteps = cleanPath.size() - 1;
        pendingObstacles = new ArrayList<>(surpriseObstacles(cleanPath));
        path = astar.planPath(world, START, BOX_CELL);
        show("Robot yenidən qutuya gedir...");
        loop(this::tickScenario2);
    }

    private void tickScenario2() {
        if (atEnd() && !pickedUp) {
            pickedUp = true;
            pickUp();
            replan(astar.planPath(world, robotCell(), GOAL));
            send();
            return;
        }
        if (atEnd()) {
            endLoop();
            dropBox();
            show("SSENARİ 2 TAMAM! Maneəsiz: " + cleanSteps + " addım | Maneəli: " + totalSteps
                    + " addım. Lidar rejimi başlanır...");
            later(2500, this::scenario3);
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            show("🤖 FUZZY: Statik maneə — vəziyyət PİS, robot gözləyir (" + (waitTicks + 1) + ")");
            return;
        }
        if (handleFaceoff()) return;
        if (pickedUp && triggerObstacle()) return;

        // Hərəkət edən robot çıxandan sonra yol D* Lite ilə saxlanır: o, robotun qarşıdakı yolunu
        // kəsən kimi D* Lite yalnız dəyişən xanaları yeniləyib yeni yol verir, robot dayanmadan davam edir
        String event = null;
        Cell movingRobot = obstacles.movingRobot();
        if (dstar != null && movingRobot != null && remainingPathContains(movingRobot)) {
            if (replan(planIncremental(robotCell(), GOAL))) {
                event = plannerEvent("🧭 D* Lite: hərəkət edən robot yolu kəsdi — yol yeniləndi");
            }
        }

        if (world.isBlocked(nextCell())) {
            // Fuzzy STOP demiş statik maneə ilə üz-üzə: robot əvvəl dayanıb gözləyir, sonra yan keçir
            if (stopAtObstacles.remove(nextCell())) {
                waitTicks = STATIC_WAIT_TICKS;
                show("🤖 FUZZY: Statik maneə ilə üz-üzə — robot DAYANIR");
                return;
            }
            Cell target = pickedUp ? GOAL : BOX_CELL;
            if (dstar != null && replan(planIncremental(robotCell(), target))) {
                status = plannerEvent("🧭 D* Lite: maneə yolu kəsdi — yol yeniləndi, yan keçir...");
            } else if (dstar == null && replan(astar.planPath(world, robotCell(), target))) {
                status = "Maneə aşkar! Yan keçir...";
            }
            send();
            return;
        }
        stepIfOperatorAllows(event != null ? event
                : pickedUp ? "Robot qutu daşıyır (maneəli yol)..." : "Robot qutuya gedir...");
        startFaceoffIfAdjacent();
    }

    /** Hərəkət edən maneə robotun qarşıdakı yolunun üstündədirmi (robotun öz xanasından sonra). */
    private boolean remainingPathContains(Cell c) {
        for (int i = robotIndex + 1; i < path.size(); i++) {
            if (path.get(i).equals(c)) return true;
        }
        return false;
    }

    /** D* Lite hadisəsini planlaşdırıcı panelində saxlayır və status üçün qaytarır. */
    private String plannerEvent(String message) {
        lastPlannerEvent = message;
        return message;
    }

    /**
     * Robot hərəkət edən robotla üz-üzə gələndə (məsafə 1) ikisi də dayanır, V2V ilə yol hüququnu
     * razılaşdırır, sonra davam edirlər. true qaytarırsa bu tick dayanmaya sərf olunub.
     */
    private boolean handleFaceoff() {
        if (faceoffLeft > 0) {
            int elapsed = faceoffTotal - faceoffLeft;
            faceoffLeft--;
            // Mesajlar dayanma müddətinə bərabər paylanır: qısa dayanmada da hamısı görünsün
            show(V2V_LINES[Math.min(elapsed * V2V_LINES.length / faceoffTotal, V2V_LINES.length - 1)]);
            return true;
        }
        if (faceoffLeft == 0) {
            faceoffLeft = -1;
            faceoffCooldown = FACEOFF_COOLDOWN_TICKS;
            obstacles.resume();
            show("📡 V2V: razılaşma tamam — hər iki robot hərəkətə davam edir");
            return true;
        }
        if (faceoffCooldown > 0) {
            faceoffCooldown--;
            return false;
        }
        return startFaceoffIfAdjacent();
    }

    /**
     * Robot ekranda görünən hərəkət edən robotla qonşu xanadadırsa ikisi də dayanır. Həm tick-in əvvəlində
     * (o robota yaxınlaşanda), həm də robot addım atandan sonra (robot ona yaxınlaşanda)
     * çağırılır: əks halda hərəkət edən robot hər tick-də birinci hərəkət etdiyi üçün yan-yana gəlmə qaçırılırdı.
     */
    private boolean startFaceoffIfAdjacent() {
        if (faceoffLeft >= 0 || faceoffCooldown > 0 || !movingRobotShownLastFrame) return false;
        if (robotCell().distanceTo(obstacles.movingRobot()) != 1) return false;
        obstacles.pause();
        faceoffTotal = FACEOFF_MIN_TICKS + random.nextInt(FACEOFF_MAX_TICKS - FACEOFF_MIN_TICKS + 1);
        faceoffLeft = faceoffTotal;
        show("🤖 İki robot üz-üzə — hər ikisi DAYANIR");
        return true;
    }

    /**
     * Robot növbəti qəfil maneəyə 2 xana yaxınlaşanda onu xəritəyə qoyur. Hərəkət edən robotun çıxış yerinə
     * {@link ScenarioConfig#MOVING_ROBOT_SPAWN_DISTANCE} xana qalanda (və ya yolun sonuna 5 xana qalanda)
     * isə gedib-gələn robotu buraxır: o, əvvəlcə uzaqda görünür, robot ona sonra yaxınlaşır.
     * Maneə çıxan anda robot dayanmır: dayanma yalnız maneə ilə üz-üzə (qonşu xanada) gələndə olur.
     * true qaytarırsa bu tick-in işi görülüb.
     */
    private boolean triggerObstacle() {
        Cell robot = robotCell();
        for (Iterator<Cell> it = pendingObstacles.iterator(); it.hasNext(); ) {
            Cell obstacle = it.next();
            // surpriseObstacles() xəritədən kənar xana da qaytara bilir (məs. sətir -2):
            // belə "görünməz maneə" üçün robot dayanmasın
            if (!world.inBounds(obstacle)) {
                it.remove();
                continue;
            }
            if (robot.distanceTo(obstacle) <= 2) {
                it.remove();
                obstacles.placeStatic(obstacle);
                changedCells.add(obstacle);   // D* Lite işləyirsə bu xananı yeniləməlidir
                // Statik maneə = "pis vəziyyət" (DISGUST + NORMAL -> HIGH -> STOP): robot maneəyə
                // qədər gəlib onunla üz-üzə dayanacaq (bax: tickScenario2-dəki isBlocked yoxlaması)
                if (obstacleFuzzy("statik maneə", 83, 55, true).action == STOP) stopAtObstacles.add(obstacle);
                stepAfterSpawn("MANEə ÇIXDI! Robot maneəyə yaxınlaşır...");
                return true;
            }
        }
        if (obstacles.movingRobot() == null
                && (robot.distanceTo(MOVING_ROBOT_START) <= MOVING_ROBOT_SPAWN_DISTANCE || path.size() - robotIndex <= 5)) {
            obstacles.startMovingRobot(MOVING_ROBOT_START.row, MOVING_ROBOT_START.col, MOVING_ROBOT_MAX_COL, MOVING_ROBOT_START.col);
            // Mühit dinamik oldu: bundan sonra yol D* Lite ilə saxlanır və artımlı yenilənir
            replan(planIncremental(robot, GOAL));
            plannerEvent("🧭 D* Lite işə düşdü: dinamik maneə — yol yenidən quruldu");
            // Hərəkətli maneə = "yaxşı vəziyyət" (HAPPY + VERY_SLOW -> NONE -> CONTINUE): robot davam edir
            if (obstacleFuzzy("hərəkətli maneə", 10, 10, false).action == CONTINUE) {
                stepAfterSpawn("HƏRƏKƏT EDƏN MANEə ÇIXDI! 🧭 D* Lite işə düşdü — robot yoluna davam edir");
            } else {
                show("HƏRƏKƏT EDƏN MANEə ÇIXDI! 🧭 D* Lite işə düşdü");
            }
            return true;
        }
        return false;
    }

    /** Maneə çıxdığı tick-də robot yerində donmur: qarşısı boşdursa bir addım atır. */
    private void stepAfterSpawn(String message) {
        if (!atEnd() && !world.isBlocked(nextCell())) advance();
        show(message);
    }

    // ==================== D* Lite planlaşdırması (ssenari 2, 3 və 4) ====================

    /**
     * Yolu D* Lite ilə tapır. Məqsəd eyni qaldıqca planlaşdırıcı əvvəlki hesablamaları yadda saxlayır
     * və yalnız dəyişən xanaları ({@link #changedCells}) yeniləyir; məqsəd dəyişəndə yenisi yaradılır.
     * Müqayisə üçün eyni sorğu A* ilə də hesablanır — bu yalnız statistikadır, robot D* Lite-ın yolu ilə gedir.
     * Yol yoxdursa null qaytarır (A*-dakı kimi).
     */
    private List<Cell> planIncremental(Cell from, Cell goal) {
        LidarSensor known = planWithLidar ? lidar : null;
        List<Cell> result;
        int before;
        boolean fresh = dstar == null || !dstar.getGoal().equals(goal);
        if (fresh) {
            dstar = new DStarLitePlanner(world, from, goal, known);
            before = 0;
            result = dstar.plan();
        } else {
            before = dstar.getExpandedCount();
            dstar.moveRobot(from);
            result = changedCells.isEmpty() ? dstar.plan() : dstar.updateCells(changedCells);
        }
        changedCells.clear();

        astar.planPath(world, from, goal, known);
        lastDstarExpanded = dstar.getExpandedCount() - before;
        lastAstarExpanded = astar.getLastExpanded();
        if (fresh) {
            initDstarExpanded += lastDstarExpanded;
            initAstarExpanded += lastAstarExpanded;
            initCount++;
        } else {
            updateDstarExpanded += lastDstarExpanded;
            updateAstarExpanded += lastAstarExpanded;
            updateCount++;
        }
        return result;
    }

    // ==================== SSENARİ 3: LiDAR ilə kəşf ====================

    /**
     * Ssenari 3 real robot kimi işləyir: planlaşdırıcı (D* Lite) yalnız LiDAR-ın gördüyünü bilir.
     * Görülməmiş xana boş sayılır (boş fəza fərziyyəsi), statik maneə görüləndən sonra yadda qalır,
     * hərəkət edən robot isə yalnız sensor zonasındadırsa bilinir. Hər tick-də sensordan gələn
     * yeni məlumat D* Lite-a verilir və yol artımlı yenilənir.
     */
    private void scenario3() {
        reset(3);
        lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, world);
        // Maneələrin yerini ssenari müəllifi bütün xəritəni bilərək seçir (robot bunu bilmir)
        pendingObstacles = new ArrayList<>(surpriseObstacles(astar.planPath(world, BOX_CELL, GOAL)));
        planWithLidar = true;
        path = planIncremental(START, BOX_CELL);
        show("LİDAR ilə kəşf başladı — D* Lite yalnız sensorun gördüyünü bilir...");
        loop(this::tickScenario3);
    }

    private void tickScenario3() {
        if (atEnd() && !pickedUp) {
            pickedUp = true;
            pickUp();
            replan(planIncremental(robotCell(), GOAL));
            show("LİDAR: Qutu götürüldü! D* Lite hədəfə yol qurdu (naməlum ərazi boş sayılır)");
            return;
        }
        if (atEnd()) {
            endLoop();
            dropBox();
            show("SSENARİ 3 TAMAM! D* Lite naməlum və dəyişən mühitdə yolu artımlı yenilədi! Qutu çatdırıldı! ("
                    + totalSteps + " addım)");
            later(2500, this::scenario4);
            return;
        }
        changedCells.addAll(lidar.scan(robotCell(), world));

        // Qutu götürüldükdən bir addım sonra maneələr anbarda peyda olur — robot onları hələ görmür,
        // LiDAR-ın zonasına düşəndə xəbər tutacaq
        if (pickedUp && !pendingObstacles.isEmpty() && robotIndex >= 1) {
            int placed = 0;
            for (Cell c : pendingObstacles) {
                if (!world.inBounds(c)) continue;
                obstacles.placeStatic(c);
                changedCells.add(c);
                placed++;
            }
            pendingObstacles.clear();
            obstacles.startMovingRobot(MOVING_ROBOT_START.row, MOVING_ROBOT_START.col, MOVING_ROBOT_MAX_COL, MOVING_ROBOT_START.col);
            changedCells.add(obstacles.movingRobot());
            show("Anbarda " + placed + " maneə və hərəkət edən robot peyda oldu — robot hələ görmür, LiDAR-la kəşf edəcək");
            return;
        }

        // Sensordan yeni məlumat gəlibsə D* Lite yolu yeniləyir; yol dəyişibsə səbəbi bildirilir
        String event = null;
        if (!changedCells.isEmpty()) {
            List<Cell> before = new ArrayList<>(path.subList(robotIndex, path.size()));
            Cell movingRobot = obstacles.movingRobot();
            boolean movingRobotSeen = movingRobot != null && lidar.isVisibleNow(movingRobot);
            // Səbəb: sensor zonasında statik maneə peyda olub (yeni kəşf və ya gözü qarşısında çıxan),
            // yoxsa hərəkət edən robot yerini dəyişib. planIncremental changedCells-i təmizlədiyi üçün əvvəl yoxlanır
            boolean staticSeen = changedCells.stream().anyMatch(c -> lidar.isDiscovered(c) && world.isBlocked(c)
                    && world.getObjectAt(c).type != ObjectType.MOVING_ROBOT);
            List<Cell> updated = planIncremental(robotCell(), pickedUp ? GOAL : BOX_CELL);
            if (updated == null) {
                endLoop();
                show("LİDAR: YOL TAPILMADI!");
                return;
            }
            replan(updated);
            if (!updated.equals(before)) {
                event = plannerEvent(staticSeen
                        ? "🧭 D* Lite: LiDAR yeni maneə kəşf etdi — yol yeniləndi"
                        : movingRobotSeen
                        ? "🧭 D* Lite: LiDAR hərəkət edən robotu izləyir — yol yeniləndi"
                        : "🧭 D* Lite: yeni sensor məlumatı — yol yeniləndi");
            }
        }
        if (world.isBlocked(nextCell())) {
            // Ehtiyat yoxlaması: qonşu xana həmişə sensor zonasındadır, D* Lite onu artıq bilməlidir
            if (replan(planIncremental(robotCell(), pickedUp ? GOAL : BOX_CELL))) {
                show(plannerEvent("🧭 D* Lite: maneə aşkar — yan keçir..."));
            } else {
                endLoop();
                show("LİDAR: YOL TAPILMADI!");
            }
            return;
        }
        // Robot yalnız əvvəlki kadrda artıq ekranda görünən hərəkət edən robota reaksiya verir: o, LiDAR
        // dumanından çıxdığı tick-də robot dayanmır (əks halda dayanma o görünməmiş başlayırdı).
        // Toqquşma riski yoxdur: onun xanası bağlıdır və yuxarıdakı isBlocked yoxlaması onu keçir.
        Cell movingRobot = obstacles.movingRobot();
        if (movingRobot != null && movingRobotShownLastFrame) {
            AttentionLevel level = movingRobotNearbyFuzzy(movingRobot);
            // Robot hərəkət edən robotla üz-üzə (qonşu xanada) dayanır. 2 xana məsafədə STOP gəlsə də robot
            // arada boş xana saxlayıb dayanmır, ehtiyatla yaxınlaşır.
            if (level.action == STOP && robotCell().distanceTo(movingRobot) == 1) {
                show("🤖 FUZZY: Hərəkət edən maneə ilə üz-üzə — robot DAYANIR");
                return;
            }
            if (level.action != CONTINUE) {
                cautious = !cautious;
                if (cautious) {
                    show("🐢 FUZZY: Ehtiyatla YAXINLAŞIR...");
                    return;
                }
            }
        }
        advance();
        show(event != null ? event
                : pickedUp ? "LİDAR: Qutu daşıyır... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)"
                : "LİDAR: Qutuya gedir...");
    }

    // ==================== SSENARİ 4: yol tam bağlıdır ====================

    private void scenario4() {
        reset(4);
        lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, world);
        // Divarlar: 3-cü və 6-cı sütun boyu və yuxarı sətir boyu forkliftlər
        int rows = world.getRows(), cols = world.getCols();
        for (int r = 0; r < rows; r++) {
            forkliftIfFree(new Cell(r, cols / 4));
            forkliftIfFree(new Cell(r, cols / 2));
        }
        for (int c = 3; c < cols; c++) forkliftIfFree(new Cell(0, c));

        planWithLidar = true;
        path = planIncremental(START, BOX_CELL);
        show("LİDAR ilə yol tam bağlandı — skan başladı...");
        loop(this::tickScenario4);
    }

    private void tickScenario4() {
        if (atEnd() && !pickedUp) {
            pickedUp = true;
            pickUp();
            show("LİDAR: Qutu götürüldü! Hədəfə gedir...");
            return;
        }
        if (atEnd() && returning) {
            endLoop();
            dropBox();
            world.getObjectAt(BOX_CELL).count = BOX_TOTAL;
            show("LİDAR+D* Lite: YOL YOXDUR! Qutu geri qoyuldu. Sübut: yol tam bağlıdır.");
            return;
        }

        // Robot yalnız gördüyünü bilir: hər addımda skan edib yolu yenidən hesablayır
        Cell pos = robotCell();
        changedCells.addAll(lidar.scan(pos, world));   // yalnız yeni görülən xanalar D* Lite-a gedir
        Cell target = pickedUp && !returning ? GOAL : BOX_CELL;
        List<Cell> before = new ArrayList<>(path.subList(robotIndex, path.size()));
        boolean sameGoal = dstar != null && dstar.getGoal().equals(target);   // hədəf dəyişməsi "kəşf" deyil
        if (!replan(planIncremental(pos, target))) {
            if (pickedUp && !returning) {
                returning = true;
                replan(planIncremental(pos, BOX_CELL));
                show(plannerEvent("🧭 D* Lite: LiDAR bütün yolların bağlı olduğunu göstərdi — qutu geri qaytarılır..."));
            } else {
                endLoop();
                show("LİDAR: HEÇ BİR YOL YOXDUR!");
            }
            return;
        }
        boolean changed = sameGoal && !path.equals(before);
        if (changed) plannerEvent("🧭 D* Lite: LiDAR yeni maneə kəşf etdi — yol yeniləndi");
        advance();
        show(changed ? "🧭 D* Lite: LiDAR yeni maneə kəşf etdi — yol yeniləndi"
                : returning ? "LİDAR: Qutu geri qaytarılır..."
                : "LİDAR: Skan... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)");
    }

    // ==================== FUZZY köməkçiləri ====================

    /**
     * Operatorun ifadə/hərəkət dəyərlərini hər dəfə təsadüfi ±15 dəyişir (real giriş yoxdur).
     * Sırf təsadüfi gəzinti 0..100 aralığında sərbəst sürüşür və uzun müddət STOP zonasında
     * ilişib qalır (robot maneəsiz yolda dəqiqələrlə dayanırdı). Ona görə hər tick dəyər
     * bazaya ({@link #OPERATOR_BASELINE}) doğru çəkilir: arabir qısa həyəcan olur, sonra sakitləşir.
     */
    private AttentionLevel randomOperator() {
        simFacial = clamp(simFacial + OPERATOR_PULL * (OPERATOR_BASELINE - simFacial) + random.nextInt(31) - 15);
        simMotion = clamp(simMotion + OPERATOR_PULL * (OPERATOR_BASELINE - simMotion) + random.nextInt(31) - 15);
        return fis.infer(simFacial, simMotion);
    }

    /**
     * Operator STOP demirsə robot bir addım atır, deyirsə bu tick gözləyir. Ardıcıl
     * {@link #MAX_OPERATOR_STOP_TICKS} tick gözlədikdən sonra operatorun diqqəti bərpa olunmuş
     * sayılır və robot davam edir, yəni maneəsiz yolda sonsuz dayanma olmur.
     */
    private void stepIfOperatorAllows(String movingStatus) {
        AttentionLevel level = randomOperator();
        boolean stop = level.action == STOP && operatorStopTicks < MAX_OPERATOR_STOP_TICKS;
        setFuzzy(String.format("🧠 FUZZY: ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                simFacial, simMotion, level, level.action), stop);
        if (stop) {
            operatorStopTicks++;
            status = "🤖 Operator diqqəti " + level + " — robot gözləyir ("
                    + operatorStopTicks + "/" + MAX_OPERATOR_STOP_TICKS + ")";
        } else {
            if (operatorStopTicks > 0) simFacial = simMotion = OPERATOR_BASELINE;
            operatorStopTicks = 0;
            advance();
            status = movingStatus;
        }
        send();
    }

    private AttentionLevel obstacleFuzzy(String label, double facial, double motion, boolean alert) {
        AttentionLevel level = fis.infer(facial, motion);
        setFuzzy(String.format("🧠 FUZZY: %s — ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                label, facial, motion, level, level.action), alert);
        return level;
    }

    /** Hərəkət edən robot 2 xanadan uzaqdırsa robot sərbəst gedir, yaxındırsa qərarı fuzzy sistem verir. */
    private AttentionLevel movingRobotNearbyFuzzy(Cell movingRobot) {
        int distance = robotCell().distanceTo(movingRobot);
        if (distance > 2) {
            setFuzzy("🧠 FUZZY: maneə uzaqdır (məsafə=" + distance + ") — sərbəst", false);
            return AttentionLevel.NONE;
        }
        AttentionLevel level = randomOperator();
        setFuzzy(String.format("🧠 FUZZY (maneə məsafə=%d): ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                distance, simFacial, simMotion, level, level.action), level.action != CONTINUE);
        return level;
    }

    // ==================== Ortaq köməkçilər ====================

    private void reset(int number) {
        scenario = number;
        world = createWorld();
        obstacles = new ObstacleManager(world);
        lidar = null;
        robotIndex = totalSteps = boxCount = 0;
        carrying = pickedUp = returning = cautious = false;
        trail.clear();
        trail.add(START);
        fuzzyInfo = "";
        fuzzyAlert = false;
        simFacial = simMotion = OPERATOR_BASELINE;
        operatorStopTicks = 0;
        pendingObstacles = new ArrayList<>();
        waitTicks = faceoffCooldown = 0;
        faceoffLeft = -1;
        movingRobotShownLastFrame = false;
        stopAtObstacles.clear();
        dstar = null;
        planWithLidar = false;
        changedCells.clear();
        lastDstarExpanded = lastAstarExpanded = 0;
        initDstarExpanded = initAstarExpanded = initCount = 0;
        updateDstarExpanded = updateAstarExpanded = updateCount = 0;
        lastPlannerEvent = null;
    }

    /**
     * Hər tick-də əvvəlcə hərəkət edən robot yerini dəyişir, sonra ssenarinin öz məntiqi işləyir.
     * Fixed delay: tick gecikəndə (məs. buludda zəif CPU) qaçırılan tick-lər ard-arda "borc kimi"
     * işlədilmir — əks halda robot bir anda bir neçə xana irəli qaçırdı.
     */
    private void loop(Runnable tick) {
        loop = executor.scheduleWithFixedDelay(safe(() -> {
            Cell movingRobotBefore = obstacles.movingRobot();
            obstacles.tick(robotCell());
            Cell movingRobotAfter = obstacles.movingRobot();
            // Hərəkət edən robotun köhnə və yeni xanası D* Lite üçün "dəyişən xana"dır. Yeni xana hər tick
            // əlavə olunur: LiDAR rejimində onun görünüb-görünməməsi robot hərəkət etdikcə də dəyişir
            if (movingRobotBefore != null) changedCells.add(movingRobotBefore);
            if (movingRobotAfter != null) changedCells.add(movingRobotAfter);
            tick.run();
        }), STEP_INTERVAL_MS, STEP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void endLoop() {
        loop.cancel(false);
    }

    private void later(int ms, Runnable action) {
        executor.schedule(safe(action), ms, TimeUnit.MILLISECONDS);
    }

    /** Executor xətanı səssizcə udmasın: xəta çap olunur və ssenari dayanır. */
    private Runnable safe(Runnable action) {
        return () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                e.printStackTrace();
                if (loop != null) loop.cancel(false);
            }
        };
    }

    private Cell robotCell() {
        return path.get(Math.min(robotIndex, path.size() - 1));
    }

    private Cell nextCell() {
        return path.get(robotIndex + 1);
    }

    private boolean atEnd() {
        return robotIndex + 1 >= path.size();
    }

    private void advance() {
        robotIndex++;
        totalSteps++;
        trail.add(robotCell());
    }

    /** Yeni yolu qəbul edir. null gəlibsə (yol yoxdur) köhnə yol qalır və false qaytarır. */
    private boolean replan(List<Cell> newPath) {
        if (newPath == null) return false;
        path = newPath;
        robotIndex = 0;
        return true;
    }

    private void pickUp() {
        world.getObjectAt(BOX_CELL).count = BOX_TOTAL - BOX_CARRY;
        carrying = true;
        boxCount = BOX_CARRY;
    }

    private void dropBox() {
        carrying = false;
        boxCount = 0;
    }

    private void forkliftIfFree(Cell c) {
        if (!world.isBlocked(c)) world.addObject(new WorldObject(c, ObjectType.FORKLIFT));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private void setFuzzy(String info, boolean alert) {
        fuzzyInfo = info;
        fuzzyAlert = alert;
    }

    private void show(String message) {
        status = message;
        send();
    }

    /** Brauzerin gözlədiyi JSON formatında vəziyyəti göndərir (index.html bu sahələri oxuyur). */
    private void send() {
        List<Map<String, Object>> objects = new ArrayList<>();
        for (int r = 0; r < world.getRows(); r++) {
            for (int c = 0; c < world.getCols(); c++) {
                Cell cell = new Cell(r, c);
                WorldObject obj = world.getObjectAt(cell);
                if (obj == null || (lidar != null && !lidar.isDiscovered(cell))) continue;
                // Hərəkət edən robot yalnız sensor onu indi görürsə göstərilir (köhnə yeri "yadda qalmır")
                if (lidar != null && obj.type == ObjectType.MOVING_ROBOT && !lidar.isVisibleNow(cell)) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("row", r);
                o.put("col", c);
                o.put("type", obj.type.name());
                o.put("count", obj.count);
                objects.add(o);
            }
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("rows", world.getRows());
        state.put("cols", world.getCols());
        state.put("robot", robotCell());
        state.put("goal", GOAL);
        state.put("stepMs", STEP_INTERVAL_MS);   // brauzer bir xanalıq animasiyanı bu müddətə yayır
        state.put("status", "SSENARİ " + scenario + ": " + status);
        state.put("carrying", carrying);
        state.put("boxCount", boxCount);
        state.put("hasLidar", lidar != null);
        state.put("fuzzyInfo", fuzzyInfo);
        state.put("fuzzyAlert", fuzzyAlert);
        if (lidar != null) {
            state.put("discovered", lidar.getDiscoveredCells());
            state.put("lidarRange", lidar.getRange());   // brauzer skan zonasını və şüaları çəkir
        }
        state.put("objects", objects);
        state.put("trail", trail);
        // Robotun qarşıdakı planlaşdırılmış yolu: D* Lite yeniləyəndə ekranda dəyişdiyi görünür
        state.put("plan", new ArrayList<>(path.subList(Math.min(robotIndex, path.size() - 1), path.size())));
        Map<String, Object> plannerInfo = new LinkedHashMap<>();
        plannerInfo.put("name", dstar != null ? "D* Lite" : "A*");
        if (dstar != null) {
            plannerInfo.put("lastDstar", lastDstarExpanded);
            plannerInfo.put("lastAstar", lastAstarExpanded);
            plannerInfo.put("initCount", initCount);
            plannerInfo.put("initDstar", initDstarExpanded);
            plannerInfo.put("initAstar", initAstarExpanded);
            plannerInfo.put("updCount", updateCount);
            plannerInfo.put("updDstar", updateDstarExpanded);
            plannerInfo.put("updAstar", updateAstarExpanded);
            if (lastPlannerEvent != null) plannerInfo.put("event", lastPlannerEvent);
        }
        state.put("planner", plannerInfo);
        publisher.accept(state);

        // Robotun hərəkət edən robota reaksiyası bu dəyərə baxır: o, ekranda görünməmiş robot dayanmasın
        Cell movingRobot = obstacles.movingRobot();
        movingRobotShownLastFrame = movingRobot != null && (lidar == null || lidar.isVisibleNow(movingRobot));
    }
}
