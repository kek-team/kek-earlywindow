#version 150 core
// Сцена KEK Launcher для раннего окна игры. Источник — src/main/resources/shaders/scene.sksl лаунчера:
// между BEGIN SCENE и END SCENE тот же текст (float2/3/4 → vec2/3/4, half → float), лаунчер сверяет его тестом.
// Кадровый буфер при выводе переворачивается (строка 0 — верх окна), поэтому gl_FragCoord — те же координаты,
// что fragCoord в Skia.
uniform float time;        // время сцены, с (замедляется при dim)
uniform vec2 resolution; // размер области, px
uniform vec2 mouse;      // курсор, -1..1, центр окна — 0
uniform float open;        // 0 — закрытый портал, 1 — раскрытый фон
uniform float pulse;       // 0..1 — энергия ядра (зажигание, дыхание)
uniform float appear;      // 0..1 — проявление портала при запуске: растёт из точки и выходит из прозрачности
uniform float dim;         // 0..1 — приглушение
uniform float flash;       // 0..1 — вспышка (вход в аккаунт или шаг в портал при старте игры)
uniform float charge;      // 0..1 — над плитой собирается вихрь, пока лаунчер готовит игру
uniform float flashLift;   // 0 — вспышка в центре окна, 1 — в центре заряда CHARGE_AT
uniform float cutout;      // 1 — окно-заставка: вне круга портала прозрачно, видно только свечение
uniform float cornerRadius; // скругление углов окна, px; за ним прозрачно
uniform vec4 particles[48]; // SceneParticles: xy — позиция (доли меньшей стороны от центра), z — яркость, w — 1 бирюзовая

// BEGIN SCENE
const vec3 BASE = vec3(0.043, 0.024, 0.078);    // #0B0614
const vec3 VIOLET = vec3(0.357, 0.165, 0.525);  // #5B2A86
const vec3 MAGENTA = vec3(0.608, 0.302, 0.792); // #9B4DCA
const vec3 TEAL = vec3(0.20, 0.78, 0.74);
const vec3 HOT = vec3(0.92, 0.80, 1.00);
// Центр заряжающегося вихря: над плитой главного экрана. Совпадает с CHARGE_Y в SceneClock.kt.
const vec2 CHARGE_AT = vec2(0.0, -0.2);

// Хэш на малых числах: на GPU большие произведения теряют точность по-разному в разных местах вызова,
// и соседние ячейки шума получали разные значения в общем узле — туманность шла квадратами.
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = mod(floor(p), 289.0);
    vec2 j = mod(i + 1.0, 289.0);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(vec2(j.x, i.y)), u.x),
               mix(hash21(vec2(i.x, j.y)), hash21(j), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

vec2 rotate(vec2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

vec4 scene(vec2 fragCoord) {
    float side = min(resolution.x, resolution.y);
    vec2 uv = (fragCoord - 0.5 * resolution) / side; // центр — 0, меньшая сторона — 1

    // Вихрь и его радиус: ядро 0.08 (при проявлении растёт от 0.02) → кольцо за краями окна.
    vec2 p = uv + mouse * 0.05;
    float r = length(p);
    float a = atan(p.y, p.x);
    float radius = mix(0.08 * (0.25 + 0.75 * appear), 1.4, open);
    float inside = smoothstep(radius + 0.04, radius - 0.04, r);

    // Туманность: закручена вокруг центра, при закрытом портале видна только внутри него.
    vec2 q = uv + mouse * 0.02;
    float swirl = (0.9 - 0.7 * open) / (length(q) + 0.25) + time * 0.04;
    vec2 nq = rotate(q, swirl);
    float n1 = fbm(nq * 2.2 + vec2(time * 0.03, -time * 0.02));
    float n2 = fbm(nq * 4.1 - vec2(time * 0.05, time * 0.01));
    vec3 nebula = VIOLET * smoothstep(0.30, 0.85, n1) * 0.9
                  + MAGENTA * smoothstep(0.50, 0.95, n2) * n1 * 0.7
                  + TEAL * smoothstep(0.80, 0.97, n2) * 0.25;
    float reveal = 0.18 + 0.82 * max(open * open, inside * smoothstep(0.0, 0.15, open));
    // Пока игра готовится, туманность сгущается вокруг собирающегося вихря.
    vec2 pc = uv + mouse * 0.05 - CHARGE_AT;
    float rc = length(pc);
    reveal *= 1.0 + 0.7 * charge * exp(-rc / 0.35);
    vec3 col = BASE + nebula * reveal;

    // Спиральные рукава вокруг ядра, ядро и кольцо; энергия гаснет к концу раскрытия.
    float energy = (0.25 + 0.75 * pulse) * (1.0 - open);
    float arms = pow(0.5 + 0.5 * sin(a * 3.0 + log(r + 0.001) * 5.0 - time * 2.5), 3.0);
    float armsReach = exp(-max(r - radius, 0.0) / 0.10) * exp(-r / 0.45);
    float ringWidth = 0.012 + 0.05 * open;
    float ringD = (r - radius) / ringWidth;
    float ring = exp(-ringD * ringD);
    float core = exp(-r / 0.035);
    // glow — то, что светится само; в заставке за кругом портала видно только оно.
    vec3 glow = MAGENTA * arms * armsReach * 1.1 * energy
                + HOT * core * 0.7 * energy
                + mix(MAGENTA, HOT, ring * 0.5) * ring * (0.35 + 0.65 * energy) * (1.0 - open * open);

    // Заряд: те же рукава, ядро и кольцо, что у портала при запуске, но над плитой и с силой по прогрессу.
    // Ветка по uniform'у одинакова для всех пикселей — вне подготовки она ничего не стоит.
    if (charge > 0.001) {
        float ac = atan(pc.y, pc.x);
        float cr = 0.015 + 0.055 * charge;
        float cArms = pow(0.5 + 0.5 * sin(ac * 3.0 + log(rc + 0.001) * 5.0 - time * 2.5), 3.0);
        float cReach = exp(-max(rc - cr, 0.0) / (0.04 + 0.1 * charge)) * exp(-rc / 0.45);
        float cRingD = (rc - cr) / 0.01;
        float cRing = exp(-cRingD * cRingD);
        float breath = 0.85 + 0.15 * cos(time * 4.0);
        glow += (MAGENTA * cArms * cReach * 1.1
               + HOT * exp(-rc / 0.03) * 0.7
               + mix(MAGENTA, HOT, cRing * 0.5) * cRing * 0.6) * charge * breath;
    }

    // Частицы: позиции считает SceneParticles на CPU; дальше 24 px от частицы свечение не считается.
    for (int i = 0; i < 48; i++) {
        vec4 pt = particles[i];
        vec2 px = pt.xy * side + 0.5 * resolution;
        vec2 d = abs(fragCoord - floor(px) - 0.5);
        if (max(d.x, d.y) < 24.0) {
            float dotv = step(max(d.x, d.y), 1.0);
            float halo = exp(-length(fragCoord - px) / 5.0) * 0.25;
            glow += mix(MAGENTA * 1.5, TEAL, pt.w) * (dotv + halo) * pt.z;
        }
    }

    // Вспышка: свечение и расходящееся кольцо — в центре окна при входе, в центре заряда при старте игры.
    float fr = length(uv - CHARGE_AT * flashLift);
    float flashD = (fr - (0.15 + 0.6 * (1.0 - flash))) / 0.05;
    float flashRing = exp(-flashD * flashD);
    glow += HOT * flash * (exp(-fr / 0.3) * 0.8 + flashRing * 0.6);
    col += glow;

    // Виньетка и приглушение.
    float vignette = smoothstep(1.25, 0.35, length(uv * vec2(0.85, 1.1)));
    float shade = mix(0.55, 1.0, vignette);
    col = clamp(BASE + (col * shade - BASE) * (1.0 - 0.55 * dim), 0.0, 1.0);

    // Заставка: внутри круга — сцена, снаружи — только свечение с альфой по яркости.
    // Цвет премультиплицирован; при open = 1 круг накрывает окно и кадр совпадает с обычным.
    vec3 g = clamp(glow * shade, 0.0, 1.0);
    float outside = max(g.r, max(g.g, g.b));
    vec3 rgb = mix(col, inside * col + (1.0 - inside) * g, cutout);
    float alpha = mix(1.0, inside + (1.0 - inside) * outside, cutout);

    // Скруглённые углы окна (расстояние до скруглённого прямоугольника, сглаживание в 1 px):
    // в конце раскрытия заставка даёт ту же форму, что основное окно.
    vec2 cq = abs(fragCoord - 0.5 * resolution) - 0.5 * resolution + cornerRadius;
    float boxD = length(max(cq, 0.0)) + min(max(cq.x, cq.y), 0.0) - cornerRadius;
    float corners = clamp(0.5 - boxD, 0.0, 1.0);
    // Проявление: цвет премультиплицирован, поэтому множитель общий.
    float fade = corners * appear;
    return vec4(vec3(rgb * fade), float(alpha * fade));
}
// END SCENE

out vec4 fragColor;

void main() {
    fragColor = scene(gl_FragCoord.xy);
}
