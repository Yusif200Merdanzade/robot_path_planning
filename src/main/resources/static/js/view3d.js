/**
 * Anbarın 3D görünüşü — yalnız vizualdır, simulyasiyaya heç bir təsiri yoxdur.
 *
 * 2D görünüş kimi serverin hər addımda göndərdiyi vəziyyəti (state) çəkir: rəflər, maneələr, qutu, hədəf,
 * planlanan yol, keçilən iz, LiDAR zonası. Robotların hamar hərəkəti və dönməsi 2D ilə eyni animasiya
 * obyektlərindən (anim, mover) götürülür, ona görə iki görünüş həmişə eyni anı göstərir.
 *
 * Koordinatlar: bir xana = 1 vahid; x = sütun, z = sətir, y = hündürlük. Sətir 0 uzaq tərəfdədir,
 * sütun 0 soldadır — yuxarıdan baxanda 2D xəritə ilə eyni düzülüş.
 * Modellər "ön tərəfi" -z istiqamətinə baxacaq şəkildə qurulur.
 *
 * Kitabxana (Three.js) saytın öz serverindən yüklənir (js/vendor): təhlükəsizlik siyasəti (CSP) yalnız
 * öz mənbəyimizdən skriptə icazə verir.
 */
import * as THREE from './vendor/three.module.min.js';
import { OrbitControls } from './vendor/OrbitControls.js';

const PALETTES = {
    dark: {
        bg: 0x0b1120, base: 0x111a2e, tileA: 0x1c2842, tileB: 0x19233b, fog: 0x060a14, wall: 0x334155,
        shelfPost: 0x7b8aa3, shelfBoard: 0x4b5a72, crate: 0xb07d3e, label: '#cbd5e1',
        goal: 0x22c55e, path: 0x38bdf8, trail: 0x22c55e, lidar: 0x38bdf8,
        hemiSky: 0xc7d7ff, hemiGround: 0x1e293b, hemi: 1.1, sun: 2.2
    },
    light: {
        bg: 0xdfe5ee, base: 0xb8c3d2, tileA: 0xf8fafc, tileB: 0xedf1f6, fog: 0x9aa7ba, wall: 0x94a3b8,
        shelfPost: 0x64748b, shelfBoard: 0x8796ab, crate: 0xc08a45, label: '#334155',
        goal: 0x16a34a, path: 0x2563eb, trail: 0x16a34a, lidar: 0x0ea5e9,
        hemiSky: 0xffffff, hemiGround: 0x94a3b8, hemi: 1.6, sun: 2.4
    }
};

const LIDAR_SWEEP_PER_SEC = 1.5;   // 2D ilə eyni: şüanın saniyədə dövr sayı
const LIDAR_TAIL = 0.9;            // şüanın arxasındakı sönən "quyruq" (radian)
const LIDAR_TAIL_STEPS = 16;
const DASH = 0.16, DASH_GAP = 0.16;
const MAX_INSTANCES = 600;
const ROBOT_SCALE = 1.2;                              // robotlar xanaya nisbətən bir az iri: uzaqdan da seçilsin
const FOLLOW_OFFSET = new THREE.Vector3(0, 3.4, 3.9);   // "Robotu izlə": kamera robotdan bu qədər yuxarıda və arxada

export function createView3D(container) {
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.localClippingEnabled = true;   // LiDAR zonası anbarın divarlarından kənara çıxmasın
    container.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 200);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.maxPolarAngle = Math.PI * 0.46;   // kamera döşəmənin altına düşməsin
    controls.minDistance = 3;
    controls.maxDistance = 40;

    const hemi = new THREE.HemisphereLight();
    scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffffff, 2);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.bias = -0.0004;
    sun.shadow.normalBias = 0.02;
    scene.add(sun, sun.target);

    // ---------- Materiallar (tema dəyişəndə rəngləri yenilənir) ----------
    const std = (color, extra) => new THREE.MeshStandardMaterial(Object.assign({ color, roughness: 0.75, metalness: 0.05 }, extra));
    const basic = (color, opacity) => new THREE.MeshBasicMaterial({
        color, transparent: opacity < 1, opacity, depthWrite: opacity >= 1
    });
    const M = {
        base: std(0), tile: std(0xffffff, { roughness: 0.9 }), wall: std(0),
        shelfPost: std(0, { metalness: 0.4, roughness: 0.45 }), shelfBoard: std(0), crate: std(0, { roughness: 0.85 }),
        forklift: std(0xef4444, { roughness: 0.5 }), forkliftDark: std(0x7f1d1d), metalDark: std(0x1f2937, { metalness: 0.5, roughness: 0.4 }),
        halo: basic(0xef4444, 0.16),
        box: std(0xc98a3d, { roughness: 0.85 }), tape: std(0xe2b77a, { roughness: 0.6 }),
        robotBody: std(0x475569, { metalness: 0.3, roughness: 0.5 }), robotTop: std(0x64748b, { metalness: 0.3, roughness: 0.5 }),
        visor: std(0x2563eb, { emissive: 0x1d4ed8, emissiveIntensity: 0.6, roughness: 0.3 }),
        cyan: std(0x67e8f9, { emissive: 0x22d3ee, emissiveIntensity: 1.2 }),
        tyre: std(0x111827, { roughness: 0.95 }),
        ledGreen: std(0x22c55e, { emissive: 0x22c55e, emissiveIntensity: 1.5 }),
        ledRed: std(0xef4444, { emissive: 0xef4444, emissiveIntensity: 1.5 }),
        moverBody: std(0xeab308, { roughness: 0.5 }), moverTrim: std(0xa16207),
        moverDeck: std(0xffffff, { map: stripeTexture(), roughness: 0.7 }),
        sensorRed: std(0xff5e3a, { emissive: 0xff5e3a, emissiveIntensity: 1.4 }),
        beacon: std(0xff8c00, { emissive: 0xff8c00, emissiveIntensity: 2 }),
        goal: std(0, { emissiveIntensity: 0.35, roughness: 0.4 }),
        goalRing: basic(0, 0.6), goalBeam: basic(0, 0.1),
        path: basic(0, 0.95), pathAssumed: basic(0, 0.35),
        trail: basic(0, 0.6), trailDot: basic(0, 0.9),
        lidarZone: basic(0, 0.13),
        lidarSweep: new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, depthWrite: false, side: THREE.DoubleSide }),
        lidarBeam: basic(0, 0.95),
        reflection: basic(0xf43f5e, 1), reflectionRay: basic(0xf43f5e, 0.55)
    };
    M.goalBeam.side = THREE.DoubleSide;
    M.goalRing.side = THREE.DoubleSide;

    // ---------- Ortaq həndəsələr ----------
    const box = (w, h, d) => new THREE.BoxGeometry(w, h, d);
    const G = {
        tile: box(0.94, 0.03, 0.94),
        post: box(0.06, 1.34, 0.06), board: box(0.88, 0.04, 0.88), crate: box(0.26, 0.2, 0.3),
        pkg: box(0.42, 0.22, 0.42), tape: box(0.43, 0.006, 0.08),
        dash: box(DASH, 0.012, 0.07), seg: box(1, 0.01, 0.07),
        dot: new THREE.CylinderGeometry(0.05, 0.05, 0.015, 12),
        zone: box(0.98, 0.01, 0.98),
        wheel: new THREE.CylinderGeometry(0.08, 0.08, 0.06, 18).rotateZ(Math.PI / 2),
        ray: box(1, 0.012, 0.012), reflection: new THREE.SphereGeometry(0.06, 14, 10),
        cargo: box(0.3, 0.16, 0.22), cargoTape: box(0.31, 0.005, 0.06)
    };

    // ---------- Sahə ----------
    const world = new THREE.Group();
    scene.add(world);
    const floorGroup = new THREE.Group();
    const staticGroup = new THREE.Group();
    world.add(floorGroup, staticGroup);

    let rows = 0, cols = 0, ox = 0, oz = 0;
    let tiles = null;
    let P = PALETTES.dark, themeName = null;
    let staticKey = '', tileKey = '';
    let lastState = null;
    let discovered = new Set();
    let follow = false;
    let lastAnim = null;
    const clipPlanes = [];
    const dummy = new THREE.Object3D();

    const robot = buildRobot();
    const moverModel = buildMover();
    const goal = buildGoal();
    robot.group.scale.setScalar(ROBOT_SCALE);
    moverModel.group.scale.setScalar(ROBOT_SCALE);
    world.add(robot.group, moverModel.group, goal.group);

    const pathKnown = instanced(G.dash, M.path);
    const pathAssumed = instanced(G.dash, M.pathAssumed);
    const trailSegs = instanced(G.seg, M.trail);
    const trailDots = instanced(G.dot, M.trailDot);
    const lidarZone = instanced(G.zone, M.lidarZone);
    const rays = instanced(G.ray, M.reflectionRay);
    const reflections = instanced(G.reflection, M.reflection);
    const sweep = new THREE.Mesh(new THREE.BufferGeometry(), M.lidarSweep);
    const beam = new THREE.Mesh(G.ray, M.lidarBeam);
    sweep.renderOrder = beam.renderOrder = 2;
    world.add(pathKnown, pathAssumed, trailSegs, trailDots, lidarZone, rays, reflections, sweep, beam);

    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);
    resize();

    // ==================== Xarici API ====================

    function render(state, anim, mover, theme, now) {
        if (!state || anim.x === null) return;
        if (state.rows !== rows || state.cols !== cols) buildFloor(state.rows, state.cols);
        if (theme !== themeName) applyTheme(theme);
        if (state !== lastState) {
            lastState = state;
            discovered = new Set((state.discovered || []).map(d => d.row + ',' + d.col));
            updateTiles(state);
            updateStatic(state);
        }

        lastAnim = anim;
        // Robotlar: mövqe və dönmə 2D animasiyasından
        placeModel(robot.group, anim.x, anim.y, anim.heading);
        robot.setCargo(state.carrying ? state.boxCount : 0);
        robot.lidarHead.rotation.y = now / 1000 * LIDAR_SWEEP_PER_SEC * 2 * Math.PI;
        moverModel.group.visible = mover.x !== null;
        if (mover.x !== null) placeModel(moverModel.group, mover.x, mover.y, mover.heading);
        const beaconOn = Math.floor(now / 400) % 2 === 0;
        M.beacon.emissiveIntensity = beaconOn ? 2.4 : 0.15;

        // Hədəf: yumşaq döyünən halqa
        const pulse = (Math.sin(now / 450) + 1) / 2;
        goal.group.position.set(state.goal.col + ox, 0, state.goal.row + oz);
        goal.ring.scale.setScalar(1 + 0.18 * pulse);
        M.goalRing.opacity = 0.75 - 0.45 * pulse;

        updatePath(state, anim, now);
        updateTrail(state, anim);
        updateLidar(state, anim, mover, now);

        if (flight) flyCamera(now);
        else if (follow) followRobot(anim);
        controls.update();
        renderer.render(scene, camera);
    }

    /** "Robotu izlə": kamera robota yaxınlaşır və onun arxasınca gedir; söndürəndə bütün anbar görünür. */
    function setFollow(on) {
        follow = on;
        if (on && lastAnim) {
            const target = new THREE.Vector3(lastAnim.x + ox, 0, lastAnim.y + oz);
            startFlight(target, target.clone().add(FOLLOW_OFFSET));
        } else {
            const view = overview();
            startFlight(view.target, view.position);
        }
    }

    function resetCamera() {
        const view = overview();
        flight = null;
        controls.target.copy(view.target);
        camera.position.copy(view.position);
        controls.update();
    }

    /**
     * Bütün anbarı ekrana sığdıran kamera mövqeyi: təxminən 55° yuxarıdan, cənub tərəfdən
     * (2D xəritə ilə eyni istiqamət — sətir 0 uzaqda, sütun 0 solda).
     */
    function overview() {
        const elevation = THREE.MathUtils.degToRad(55);
        const vHalf = THREE.MathUtils.degToRad(camera.fov / 2);
        const hHalf = Math.atan(Math.tan(vHalf) * camera.aspect);
        const fitWidth = (cols + 0.8) / 2 / Math.tan(hHalf);
        const fitDepth = ((rows + 0.8) / 2 * Math.sin(elevation) + 0.6) / Math.tan(vHalf);
        const distance = Math.max(fitWidth, fitDepth) * 1.04 + (rows + 0.8) / 2 * Math.cos(elevation);
        const target = new THREE.Vector3(0, 0, 0.2);
        const position = new THREE.Vector3(0, distance * Math.sin(elevation), 0.2 + distance * Math.cos(elevation));
        return { target, position };
    }

    // Kameranın bir mövqedən digərinə yumşaq keçidi (~0.9 san)
    let flight = null;
    function startFlight(target, position) {
        flight = {
            fromTarget: controls.target.clone(), toTarget: target,
            fromPos: camera.position.clone(), toPos: position,
            start: performance.now(), duration: 900
        };
    }

    function flyCamera(now) {
        const t = Math.min((now - flight.start) / flight.duration, 1);
        const e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;   // yumşaq başlayıb yumşaq dayanır
        controls.target.lerpVectors(flight.fromTarget, flight.toTarget, e);
        camera.position.lerpVectors(flight.fromPos, flight.toPos, e);
        if (t >= 1) flight = null;
    }

    // ==================== Qurulma ====================

    function buildFloor(r, c) {
        rows = r;
        cols = c;
        ox = -(cols - 1) / 2;
        oz = -(rows - 1) / 2;
        disposeChildren(floorGroup);

        const base = new THREE.Mesh(box(cols + 0.6, 0.2, rows + 0.6), M.base);
        base.position.y = -0.1;
        base.receiveShadow = true;
        floorGroup.add(base);

        tiles = new THREE.InstancedMesh(G.tile, M.tile, rows * cols);
        tiles.receiveShadow = true;
        for (let row = 0; row < rows; row++) {
            for (let col = 0; col < cols; col++) {
                dummy.position.set(col + ox, 0.015, row + oz);
                dummy.rotation.set(0, 0, 0);
                dummy.scale.set(1, 1, 1);
                dummy.updateMatrix();
                tiles.setMatrixAt(row * cols + col, dummy.matrix);
                tiles.setColorAt(row * cols + col, new THREE.Color(0xffffff));
            }
        }
        floorGroup.add(tiles);

        // Alçaq divar: anbarın sərhədi
        const h = 0.32, t = 0.12, w = cols + 0.6, d = rows + 0.6;
        for (const [sx, sz, gx, gz] of [[w, t, 0, -d / 2], [w, t, 0, d / 2], [t, d, -w / 2, 0], [t, d, w / 2, 0]]) {
            const wall = new THREE.Mesh(box(sx, h, sz), M.wall);
            wall.position.set(gx, h / 2 - 0.05, gz);
            wall.castShadow = wall.receiveShadow = true;
            floorGroup.add(wall);
        }

        // LiDAR zonası divardan kənara çıxmasın: 4 kəsici müstəvi
        const minX = ox - 0.5, maxX = ox + cols - 0.5, minZ = oz - 0.5, maxZ = oz + rows - 0.5;
        clipPlanes.length = 0;
        clipPlanes.push(
            new THREE.Plane(new THREE.Vector3(1, 0, 0), -minX), new THREE.Plane(new THREE.Vector3(-1, 0, 0), maxX),
            new THREE.Plane(new THREE.Vector3(0, 0, 1), -minZ), new THREE.Plane(new THREE.Vector3(0, 0, -1), maxZ));
        for (const m of [M.lidarZone, M.lidarSweep, M.lidarBeam]) m.clippingPlanes = clipPlanes;

        const span = Math.max(rows, cols);
        sun.position.set(-cols * 0.35, span * 1.4, rows * 0.45);
        sun.target.position.set(0, 0, 0);
        const sc = sun.shadow.camera;
        sc.left = sc.bottom = -span * 0.8;
        sc.right = sc.top = span * 0.8;
        sc.near = 1;
        sc.far = span * 4;
        sc.updateProjectionMatrix();

        staticKey = tileKey = '';
        lastState = null;   // yeni ölçüdə hər şey yenidən qurulsun
        themeName = null;   // koordinat yazıları yenidən çəkilsin
        resetCamera();
    }

    function applyTheme(theme) {
        themeName = theme;
        P = PALETTES[theme] || PALETTES.dark;
        scene.background = new THREE.Color(P.bg);
        hemi.color.setHex(P.hemiSky);
        hemi.groundColor.setHex(P.hemiGround);
        hemi.intensity = P.hemi;
        sun.intensity = P.sun;
        M.base.color.setHex(P.base);
        M.wall.color.setHex(P.wall);
        M.shelfPost.color.setHex(P.shelfPost);
        M.shelfBoard.color.setHex(P.shelfBoard);
        M.crate.color.setHex(P.crate);
        M.goal.color.setHex(P.goal);
        M.goal.emissive.setHex(P.goal);
        M.goalRing.color.setHex(P.goal);
        M.goalBeam.color.setHex(P.goal);
        M.path.color.setHex(P.path);
        M.pathAssumed.color.setHex(P.path);
        M.trail.color.setHex(P.trail);
        M.trailDot.color.setHex(P.trail);
        M.lidarZone.color.setHex(P.lidar);
        M.lidarBeam.color.setHex(P.lidar);
        // Döşəmə və qutu yazısı (×3) yeni rənglərlə yenidən qurulsun
        tileKey = staticKey = '';
        if (lastState) {
            updateTiles(lastState);
            updateStatic(lastState);
        }
        buildAxisLabels();
    }

    /** Döşəmə rəngləri: şahmat naxışı, LiDAR rejimində görülməmiş xanalar tünd ("duman"). */
    function updateTiles(state) {
        const key = themeName + '|' + state.hasLidar + '|' + discovered.size + '|' + (state.discovered ? state.discovered[0]?.row + ',' + state.discovered[0]?.col : '');
        if (key === tileKey || !tiles) return;
        tileKey = key;
        const color = new THREE.Color();
        for (let row = 0; row < rows; row++) {
            for (let col = 0; col < cols; col++) {
                const fog = state.hasLidar && !discovered.has(row + ',' + col);
                color.setHex(fog ? P.fog : (row + col) % 2 === 0 ? P.tileA : P.tileB);
                tiles.setColorAt(row * cols + col, color);
            }
        }
        tiles.instanceColor.needsUpdate = true;
    }

    /** Rəf, forklift və qutu: yalnız xəritə dəyişəndə yenidən qurulur (hərəkət edən robot ayrıca çəkilir). */
    function updateStatic(state) {
        const objects = state.objects.filter(o => o.type !== 'MOVING_ROBOT');
        const key = objects.map(o => o.type + o.row + ',' + o.col + 'x' + o.count).join('|');
        if (key === staticKey) return;
        staticKey = key;
        disposeChildren(staticGroup, true);
        for (const o of objects) {
            const model = o.type === 'SHELF' ? buildShelf(o.row, o.col)
                : o.type === 'FORKLIFT' ? buildForklift()
                : o.type === 'PACKAGE' ? buildPackage(o.count) : null;
            if (!model) continue;
            model.position.set(o.col + ox, 0, o.row + oz);
            staticGroup.add(model);
        }
    }

    // ==================== Modellər ====================

    function mesh(geometry, material, x, y, z, shadow = true) {
        const m = new THREE.Mesh(geometry, material);
        m.position.set(x, y, z);
        m.castShadow = shadow;
        m.receiveShadow = true;
        return m;
    }

    /** Rəf: 4 dirək, 4 taxta, üstündə qutular (sayı xanaya görə dəyişir ki, bütün rəflər eyni görünməsin). */
    function buildShelf(row, col) {
        const g = new THREE.Group();
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) g.add(mesh(G.post, M.shelfPost, sx * 0.41, 0.67, sz * 0.41));
        const levels = [0.08, 0.5, 0.92, 1.32];
        levels.forEach(y => g.add(mesh(G.board, M.shelfBoard, 0, y, 0)));
        for (let l = 0; l < 3; l++) {
            const n = 1 + (row * 7 + col * 3 + l) % 3;
            for (let k = 0; k < n; k++) {
                const crate = mesh(G.crate, M.crate, (k - (n - 1) / 2) * 0.28, levels[l] + 0.12, ((row + l + k) % 2) * 0.1 - 0.05);
                g.add(crate);
            }
        }
        return g;
    }

    /** Forklift: qırmızı gövdə, əks yük, qoruyucu çərçivə, dirək və çəngəllər; altında qırmızı "xəbərdarlıq" zonası. */
    function buildForklift() {
        const g = new THREE.Group();
        const halo = new THREE.Mesh(G.zone, M.halo);
        halo.position.y = 0.035;
        g.add(halo);
        g.add(mesh(box(0.5, 0.3, 0.56), M.forklift, 0, 0.27, 0.06));
        g.add(mesh(box(0.5, 0.24, 0.14), M.forkliftDark, 0, 0.3, 0.33));
        for (const sx of [-1, 1]) {
            for (const sz of [-0.08, 0.26]) g.add(mesh(box(0.04, 0.46, 0.04), M.metalDark, sx * 0.22, 0.65, sz));
            g.add(mesh(box(0.05, 0.95, 0.05), M.metalDark, sx * 0.17, 0.5, -0.27));
            g.add(mesh(box(0.06, 0.03, 0.22), M.metalDark, sx * 0.12, 0.06, -0.4));
            for (const sz of [-0.12, 0.22]) {
                const w = mesh(G.wheel, M.tyre, sx * 0.27, 0.08, sz);
                g.add(w);
            }
        }
        g.add(mesh(box(0.5, 0.03, 0.4), M.metalDark, 0, 0.88, 0.09));
        return g;
    }

    function buildPackage(count) {
        const g = new THREE.Group();
        for (let i = 0; i < count; i++) {
            const y = 0.12 + i * 0.23;
            const b = mesh(G.pkg, M.box, 0, y, 0);
            b.rotation.y = (i % 2) * 0.25;
            const t = mesh(G.tape, M.tape, 0, 0.111, 0, false);
            b.add(t);
            g.add(b);
        }
        if (count > 1) {
            const label = textSprite('×' + count, P.label, 0.32);
            label.position.set(0, 0.25 + count * 0.23, 0);
            g.add(label);
        }
        return g;
    }

    /** Bizim robot (AMR): metal gövdə, mavi ön panel, üstdə fırlanan LiDAR, önündə çəngəllər və yük. */
    function buildRobot() {
        const g = new THREE.Group();
        g.add(mesh(box(0.56, 0.18, 0.62), M.robotBody, 0, 0.16, 0));
        g.add(mesh(box(0.44, 0.04, 0.46), M.robotTop, 0, 0.27, 0.02));
        g.add(mesh(box(0.44, 0.08, 0.04), M.visor, 0, 0.2, -0.31));
        for (const sx of [-1, 1]) {
            g.add(mesh(new THREE.SphereGeometry(0.025, 10, 8), M.cyan, sx * 0.11, 0.21, -0.335, false));
            for (const sz of [-1, 1]) g.add(mesh(G.wheel, M.tyre, sx * 0.31, 0.08, sz * 0.18));
            g.add(mesh(box(0.04, 0.03, 0.24), M.metalDark, sx * 0.16, 0.11, -0.42));
        }
        g.add(mesh(new THREE.SphereGeometry(0.025, 10, 8), M.ledGreen, -0.15, 0.2, 0.315, false));
        g.add(mesh(new THREE.SphereGeometry(0.025, 10, 8), M.ledRed, 0.15, 0.2, 0.315, false));
        g.add(mesh(new THREE.CylinderGeometry(0.1, 0.11, 0.08, 20), M.metalDark, 0, 0.33, 0.04));
        const lidarHead = new THREE.Group();
        lidarHead.position.set(0, 0.39, 0.04);
        lidarHead.add(mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.05, 16), M.cyan, 0, 0, 0, false));
        lidarHead.add(mesh(box(0.02, 0.03, 0.08), M.metalDark, 0, 0.01, -0.06, false));
        g.add(lidarHead);

        const cargo = new THREE.Group();
        g.add(cargo);
        let cargoCount = -1;
        function setCargo(n) {
            if (n === cargoCount) return;
            cargoCount = n;
            cargo.clear();
            for (let i = 0; i < n; i++) {
                const b = mesh(G.cargo, M.box, 0, 0.21 + i * 0.17, -0.42);
                b.add(mesh(G.cargoTape, M.tape, 0, 0.081, 0, false));
                cargo.add(b);
            }
        }
        return { group: g, lidarHead, setCargo };
    }

    /** Hərəkət edən başqa robot: sarı gövdə, zolaqlı yük platforması, yanıb-sönən narıncı mayak. */
    function buildMover() {
        const g = new THREE.Group();
        g.add(mesh(box(0.64, 0.05, 0.7), M.moverTrim, 0, 0.08, 0));
        g.add(mesh(box(0.6, 0.18, 0.66), M.moverBody, 0, 0.18, 0));
        g.add(mesh(box(0.46, 0.03, 0.46), M.moverDeck, 0, 0.285, 0.04));
        g.add(mesh(box(0.4, 0.06, 0.04), M.metalDark, 0, 0.2, -0.34));
        for (const sx of [-1, 1]) {
            g.add(mesh(new THREE.SphereGeometry(0.025, 10, 8), M.sensorRed, sx * 0.1, 0.2, -0.365, false));
            for (const sz of [-1, 1]) g.add(mesh(G.wheel, M.tyre, sx * 0.33, 0.08, sz * 0.2));
        }
        g.add(mesh(new THREE.CylinderGeometry(0.07, 0.07, 0.1, 16), M.beacon, 0, 0.35, 0.04, false));
        return { group: g };
    }

    function buildGoal() {
        const g = new THREE.Group();
        g.add(mesh(box(0.84, 0.03, 0.84), M.goal, 0, 0.035, 0, false));
        const ring = new THREE.Mesh(new THREE.RingGeometry(0.46, 0.52, 48).rotateX(-Math.PI / 2), M.goalRing);
        ring.position.y = 0.06;
        g.add(ring);
        const beamMesh = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 2, 32, 1, true), M.goalBeam);
        beamMesh.position.y = 1;
        g.add(beamMesh);
        const label = textSprite('HƏDƏF', '#ffffff', 0.34, true);
        label.position.y = 2.25;
        g.add(label);
        return { group: g, ring };
    }

    // ==================== Hər kadrda yenilənənlər ====================

    function placeModel(group, x, y, heading) {
        group.position.set(x + ox, 0, y + oz);
        // 2D bucağı (x sağa, y aşağı) → 3D fırlanma: model -z istiqamətinə baxır
        group.rotation.y = Math.atan2(-Math.cos(heading), -Math.sin(heading));
    }

    /** Planlanan yol: döşəmədə robotdan hədəfə doğru "axan" qırıq xətt; LiDAR-ın görmədiyi hissə solğun. */
    function updatePath(state, anim, now) {
        let known = 0, assumed = 0;
        if (state.plan && state.plan.length > 1) {
            const spacing = DASH + DASH_GAP;
            const phase = (now / 1000 * 0.7) % spacing;
            let px = anim.x, pz = anim.y;
            for (let i = 1; i < state.plan.length; i++) {
                const p = state.plan[i];
                const dx = p.col - px, dz = p.row - pz;
                const len = Math.hypot(dx, dz);
                const isAssumed = state.hasLidar && !discovered.has(p.row + ',' + p.col);
                const target = isAssumed ? pathAssumed : pathKnown;
                for (let s = phase; s + DASH <= len && known + assumed < MAX_INSTANCES; s += spacing) {
                    const t = (s + DASH / 2) / len;
                    dummy.position.set(px + dx * t + ox, 0.045, pz + dz * t + oz);
                    dummy.rotation.set(0, Math.atan2(-dz, dx), 0);
                    dummy.scale.set(1, 1, 1);
                    dummy.updateMatrix();
                    target.setMatrixAt(isAssumed ? assumed++ : known++, dummy.matrix);
                }
                px = p.col;
                pz = p.row;
            }
        }
        commit(pathKnown, known);
        commit(pathAssumed, assumed);
    }

    /** Keçilən iz: yaşıl zolaq və hər keçilən xanada nöqtə; son hissə robotun hazırkı yerinə qədər. */
    function updateTrail(state, anim) {
        let segs = 0, dots = 0;
        const trail = state.trail;
        for (let i = 0; i < trail.length - 1 && segs < MAX_INSTANCES; i++) {
            const a = trail[i];
            const b = i === trail.length - 2 ? { col: anim.x, row: anim.y } : trail[i + 1];
            const dx = b.col - a.col, dz = b.row - a.row;
            const len = Math.hypot(dx, dz);
            if (len > 0.001) {
                dummy.position.set((a.col + b.col) / 2 + ox, 0.04, (a.row + b.row) / 2 + oz);
                dummy.rotation.set(0, Math.atan2(-dz, dx), 0);
                dummy.scale.set(len, 1, 1);
                dummy.updateMatrix();
                trailSegs.setMatrixAt(segs++, dummy.matrix);
            }
            dummy.position.set(a.col + ox, 0.045, a.row + oz);
            dummy.rotation.set(0, 0, 0);
            dummy.scale.set(1, 1, 1);
            dummy.updateMatrix();
            trailDots.setMatrixAt(dots++, dummy.matrix);
        }
        commit(trailSegs, segs);
        commit(trailDots, dots);
    }

    /**
     * LiDAR (ssenari 3-4): 2D ilə eyni məna — skan zonası (romb), fırlanan şüa və maneələrdən lazer əksləri.
     * Sensorun məntiqi serverdədir, burada yalnız onun gördüyü göstərilir.
     */
    function updateLidar(state, anim, mover, now) {
        const on = state.hasLidar && state.lidarRange;
        sweep.visible = beam.visible = !!on;
        if (!on) {
            commit(lidarZone, 0);
            commit(rays, 0);
            commit(reflections, 0);
            return;
        }
        const range = state.lidarRange;
        const rr = state.robot.row, rc = state.robot.col;
        let z = 0;
        for (let dr = -range; dr <= range; dr++) {
            for (let dc = -range; dc <= range; dc++) {
                const r = rr + dr, c = rc + dc;
                if (Math.abs(dr) + Math.abs(dc) > range || !discovered.has(r + ',' + c)) continue;
                dummy.position.set(c + ox, 0.04, r + oz);
                dummy.rotation.set(0, 0, 0);
                dummy.scale.set(1, 1, 1);
                dummy.updateMatrix();
                lidarZone.setMatrixAt(z++, dummy.matrix);
            }
        }
        commit(lidarZone, z);

        // Fırlanan şüa: romb sərhədinə qədər uzanan, arxası sönən sektor
        const reach = range + 0.5;
        const angle = now / 1000 * LIDAR_SWEEP_PER_SEC * 2 * Math.PI;
        const rx = anim.x + ox, rz = anim.y + oz, y = 0.06;
        const radius = a => reach / (Math.abs(Math.cos(a)) + Math.abs(Math.sin(a)));
        const lidarColor = new THREE.Color(P.lidar);
        const positions = [], colors = [];
        for (let i = 0; i < LIDAR_TAIL_STEPS; i++) {
            const a0 = angle - LIDAR_TAIL * (i + 1) / LIDAR_TAIL_STEPS, a1 = angle - LIDAR_TAIL * i / LIDAR_TAIL_STEPS;
            const alpha0 = 0.32 * (1 - (i + 1) / LIDAR_TAIL_STEPS), alpha1 = 0.32 * (1 - i / LIDAR_TAIL_STEPS);
            positions.push(rx, y, rz,
                rx + Math.cos(a1) * radius(a1), y, rz + Math.sin(a1) * radius(a1),
                rx + Math.cos(a0) * radius(a0), y, rz + Math.sin(a0) * radius(a0));
            colors.push(lidarColor.r, lidarColor.g, lidarColor.b, alpha1,
                lidarColor.r, lidarColor.g, lidarColor.b, alpha1,
                lidarColor.r, lidarColor.g, lidarColor.b, alpha0);
        }
        sweep.geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
        sweep.geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 4));
        sweep.geometry.computeBoundingSphere();

        const len = radius(angle);
        beam.position.set(rx + Math.cos(angle) * len / 2, y + 0.005, rz + Math.sin(angle) * len / 2);
        beam.rotation.set(0, -angle, 0);
        beam.scale.set(len, 1.5, 1.5);

        // Lazer əksləri: zonadakı maneələrə robotdan qırmızı şüa və əks nöqtəsi
        let n = 0;
        for (const obj of state.objects) {
            if (obj.type === 'PACKAGE' || n >= 40) continue;
            if (Math.abs(obj.row - rr) + Math.abs(obj.col - rc) > range) continue;
            const moving = obj.type === 'MOVING_ROBOT' && mover.x !== null;
            const tx = (moving ? mover.x : obj.col) + ox, tz = (moving ? mover.y : obj.row) + oz;
            const ty = 0.32;
            const dx = tx - rx, dz = tz - rz, dy = ty - 0.39;
            const flat = Math.hypot(dx, dz);
            dummy.position.set((rx + tx) / 2, (0.39 + ty) / 2, (rz + tz) / 2);
            dummy.rotation.set(0, Math.atan2(-dz, dx), Math.atan2(dy, flat), 'YZX');
            dummy.scale.set(Math.hypot(flat, dy), 1, 1);
            dummy.updateMatrix();
            rays.setMatrixAt(n, dummy.matrix);
            dummy.position.set(tx, ty, tz);
            dummy.rotation.set(0, 0, 0);
            dummy.scale.set(1, 1, 1);
            dummy.updateMatrix();
            reflections.setMatrixAt(n, dummy.matrix);
            n++;
        }
        commit(rays, n);
        commit(reflections, n);
    }

    /** "Robotu izlə": kamera robotla birlikdə sürüşür, istifadəçi yenə fırlada və yaxınlaşdıra bilir. */
    function followRobot(anim) {
        const target = new THREE.Vector3(anim.x + ox, 0, anim.y + oz);
        const delta = target.sub(controls.target).multiplyScalar(0.08);
        controls.target.add(delta);
        camera.position.add(delta);
    }

    // ==================== Köməkçilər ====================

    function instanced(geometry, material) {
        const m = new THREE.InstancedMesh(geometry, material, MAX_INSTANCES);
        m.count = 0;
        m.frustumCulled = false;
        return m;
    }

    function commit(m, count) {
        m.count = count;
        m.instanceMatrix.needsUpdate = true;
    }

    let labels = [];
    function buildAxisLabels() {
        for (const l of labels) {
            floorGroup.remove(l);
            l.material.map.dispose();
            l.material.dispose();
        }
        labels = [];
        for (let c = 0; c < cols; c++) labels.push(placeLabel(String(c), c + ox, oz - 0.95));
        for (let r = 0; r < rows; r++) labels.push(placeLabel(String(r), ox - 0.95, r + oz));
    }

    function placeLabel(text, x, z) {
        const s = textSprite(text, P.label, 0.28);
        s.position.set(x, 0.12, z);
        floorGroup.add(s);
        return s;
    }

    function textSprite(text, color, height, pill) {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        const font = '700 64px "Inter", "Segoe UI", system-ui, sans-serif';
        ctx.font = font;
        const w = Math.ceil(ctx.measureText(text).width) + (pill ? 56 : 16);
        canvas.width = w;
        canvas.height = 96;
        ctx.font = font;
        if (pill) {
            ctx.fillStyle = 'rgba(22,163,74,0.92)';
            roundRect(ctx, 2, 6, w - 4, 84, 36);
        }
        ctx.fillStyle = color;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, w / 2, 50);
        const texture = new THREE.CanvasTexture(canvas);
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = 4;
        const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, depthWrite: false }));
        sprite.scale.set(height * w / 96, height, 1);
        return sprite;
    }

    function roundRect(ctx, x, y, w, h, r) {
        ctx.beginPath();
        ctx.roundRect(x, y, w, h, r);
        ctx.fill();
    }

    function disposeChildren(group, deep) {
        for (const child of [...group.children]) {
            group.remove(child);
            child.traverse(o => {
                // Ortaq həndəsə və materiallar silinmir; yalnız bu obyektə məxsus olanlar (yazı, xüsusi qutu)
                if (o.isSprite) {
                    o.material.map.dispose();
                    o.material.dispose();
                } else if (deep && o.isMesh && !Object.values(G).includes(o.geometry)) {
                    o.geometry.dispose();
                }
            });
        }
    }

    function resize() {
        const w = container.clientWidth, h = container.clientHeight;
        if (!w || !h) return;
        renderer.setSize(w, h, false);
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
    }

    return { render, setFollow, resetCamera };
}

/** Hərəkət edən robotun yük platforması üçün qara-sarı xəbərdarlıq zolaqları. */
function stripeTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#1f2937';
    ctx.fillRect(0, 0, 64, 64);
    ctx.strokeStyle = '#eab308';
    ctx.lineWidth = 9;
    for (let k = -64; k < 128; k += 22) {
        ctx.beginPath();
        ctx.moveTo(k, 64);
        ctx.lineTo(k + 64, 0);
        ctx.stroke();
    }
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
}
