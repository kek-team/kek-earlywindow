/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Изменено KEK.Team: перенесён DisplayWindow без RenderElement/ColourScheme/PerformanceInfo; кадр рисует
 * подключаемый рисовальщик в буфер размером с окно, до 30 кадров/с; заголовок «Iskra» и иконка искры;
 * сбой до появления кадра бросает исключение (KekEarlyWindow откатывается на DisplayWindow) вместо диалога.
 */

package team.kek.earlywindow;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL32C.*;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.kek.earlywindow.gl.EarlyFramebuffer;
import team.kek.earlywindow.gl.QuadShader;
import team.kek.earlywindow.gl.SimpleFont;

/**
 * Окно загрузки: создаётся сразу при старте FML, до передачи игре его рисует отдельный поток, после —
 * главный поток игры из {@link #periodicTick()}.
 */
public class PortalWindow implements ImmediateWindowProvider, AutoCloseable {
    /** Заголовок окна, как {@code EarlyWindowTheme.GAME_TITLE} лаунчера и конфиг Custom Window Title пака. */
    public static final String TITLE = "Iskra";
    /** Фон до первого кадра сцены — {@code BASE} шейдера. */
    static final int BACKGROUND = 0x0B0614;
    private static final int[][] GL_VERSIONS = new int[][] { { 4, 6 }, { 4, 5 }, { 4, 4 }, { 4, 3 }, { 4, 2 }, { 4, 1 }, { 4, 0 }, { 3, 3 }, { 3, 2 } };
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");
    private static final long TICK_MS = 33;
    // На такт меньше периода: иначе дрожание планировщика пропускало бы каждый второй кадр.
    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(TICK_MS - 2);
    private static final String ERROR_URL = "https://links.neoforged.net/early-display-errors";

    private QuadShader quads;
    private SimpleFont font;
    private EarlyFramebuffer framebuffer;
    private ScheduledFuture<?> windowTick;
    private ScheduledFuture<?> initializationFuture;
    private long window;
    private ScheduledExecutorService renderScheduler;
    private volatile int fbWidth;
    private volatile int fbHeight;
    private int winWidth;
    private int winHeight;
    private int winX;
    private int winY;
    private final Semaphore renderLock = new Semaphore(1);
    private boolean maximized;
    private String glVersion;
    private Runnable repaintTick = () -> {};
    private long nextFrameTime = 0;
    private long startNanos;
    private final SceneLayer scene = new SceneLayer();
    private volatile Consumer<FrameContext> painter = ctx -> scene.paint(ctx, 0f, -1f);
    private boolean iconPending;
    private Constructor<?> vanillaOverlay;

    @Override
    public String name() {
        return KekEarlyWindow.NAME;
    }

    /** Чем рисовать кадр; вызывается на потоке, владеющем контекстом GL. */
    public void setFrame(Consumer<FrameContext> painter) {
        this.painter = painter;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        final OptionParser parser = new OptionParser();
        var mcversionopt = parser.accepts("fml.mcVersion").withRequiredArg().ofType(String.class);
        var forgeversionopt = parser.accepts("fml.neoForgeVersion").withRequiredArg().ofType(String.class);
        var widthopt = parser.accepts("width")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightopt = parser.accepts("height")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedopt = parser.accepts("earlywindow.maximized");
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments);
        winWidth = parsed.valueOf(widthopt);
        winHeight = parsed.valueOf(heightopt);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);
        this.maximized = parsed.has(maximizedopt) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        var forgeVersion = parsed.valueOf(forgeversionopt);
        StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("NeoForge loading " + forgeVersion));
        return start(parsed.valueOf(mcversionopt));
    }

    /**
     * Окно и поток отрисовки. В отличие от DisplayWindow, ждёт готовности GL: ошибка здесь — откат на
     * стандартный экран, а не диалог и выход.
     */
    private Runnable start(@Nullable String mcVersion) {
        renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final var thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            thread.setName("kek-earlywindow");
            return thread;
        });
        initWindow(mcVersion);
        this.initializationFuture = renderScheduler.schedule(this::initRender, 1, TimeUnit.MILLISECONDS);
        try {
            this.initializationFuture.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Не удалось подготовить отрисовку", e.getCause());
        } catch (InterruptedException | TimeoutException e) {
            throw new IllegalStateException("Не удалось подготовить отрисовку", e);
        }
        return this::periodicTick;
    }

    private void renderThreadFunc() {
        if (!renderLock.tryAcquire()) {
            return;
        }
        try {
            long nt;
            if ((nt = System.nanoTime()) < nextFrameTime) {
                return;
            }
            nextFrameTime = nt + MINFRAMETIME;
            glfwMakeContextCurrent(window);
            if (this.windowTick == null) {
                // окно уже у игры: колбэки размера сняты, а мы на главном потоке
                int[] w = new int[1];
                int[] h = new int[1];
                glfwGetFramebufferSize(window, w, h);
                if (w[0] > 0 && h[0] > 0) {
                    fbWidth = w[0];
                    fbHeight = h[0];
                }
            }
            paintFrame(fbWidth, fbHeight);
            glViewport(0, 0, fbWidth, fbHeight);
            framebuffer.draw();
            glfwSwapBuffers(window);
        } catch (Throwable t) {
            LOGGER.error("Ошибка кадра", t);
        } finally {
            if (this.windowTick != null) glfwMakeContextCurrent(0); // отдаём контекст, если рисуем не на главном потоке
            renderLock.release();
        }
    }

    /** Рисует кадр в {@link #framebuffer} размером {@code width × height}. */
    private void paintFrame(int width, int height) {
        framebuffer.resize(width, height);
        framebuffer.activate();
        glViewport(0, 0, width, height);
        glClearColor((BACKGROUND >> 16 & 0xFF) / 255f, (BACKGROUND >> 8 & 0xFF) / 255f, (BACKGROUND & 0xFF) / 255f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        painter.accept(new FrameContext(width, height, System.nanoTime() - startNanos, quads, font));
        framebuffer.deactivate();
    }

    private void initRender() {
        glfwMakeContextCurrent(window);
        try {
            glfwSwapInterval(1);
            createCapabilities();
            LOGGER.info("GL info: " + glGetString(GL_RENDERER) + " GL version " + glGetString(GL_VERSION) + ", " + glGetString(GL_VENDOR));
            quads = new QuadShader();
            quads.init();
            framebuffer = new EarlyFramebuffer(fbWidth, fbHeight);
            font = new SimpleFont("team/kek/earlywindow/Monocraft.ttf", 18);
            startNanos = System.nanoTime();
        } finally {
            glfwMakeContextCurrent(0);
        }
        this.windowTick = renderScheduler.scheduleAtFixedRate(this::renderThreadFunc, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getGLVersion() {
        return this.glVersion;
    }

    private final ReentrantLock crashLock = new ReentrantLock();

    private void crashElegantly(String errorDetails) {
        crashLock.lock(); // Crash at most once!

        StringBuilder msgBuilder = new StringBuilder(2000);
        msgBuilder.append("Не удалось запустить загрузку игры и её окно.\n");
        msgBuilder.append("\n\n");
        msgBuilder.append("Подробности:\n");
        msgBuilder.append(errorDetails);
        msgBuilder.append("\n\n");
        msgBuilder.append("Нажмите «Да», чтобы открыть " + ERROR_URL + " в браузере");
        LOGGER.error("ERROR DISPLAY\n{}", msgBuilder);
        var thread = new Thread(() -> {
            var res = TinyFileDialogs.tinyfd_messageBox(TITLE, msgBuilder.toString(), "yesno", "error", false);
            if (res) {
                try {
                    Desktop.getDesktop().browse(URI.create(ERROR_URL));
                } catch (IOException ioe) {
                    TinyFileDialogs.tinyfd_messageBox(TITLE, "Не удалось открыть браузер.\nАдрес: " + ERROR_URL, "ok", "error", false);
                }
            }
        }, "crash-report");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ignored) {}

        System.exit(1);
    }

    private void initWindow(@Nullable String mcVersion) {
        long glfwInitBegin = System.nanoTime();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        long glfwInitEnd = System.nanoTime();

        if (glfwInitEnd - glfwInitBegin > 1e9) {
            LOGGER.error("WARNING : glfwInit took {} seconds to start.", (glfwInitEnd - glfwInitBegin) / 1.0e9);
        }

        // Clear the Last Exception (#7285 - Prevent Vanilla throwing an IllegalStateException due to invalid controller mappings)
        handleLastGLFWError((error, description) -> LOGGER.error(String.format("Suppressing Last GLFW error: [0x%X]%s", error, description)));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        if (mcVersion != null) {
            // this emulates what we would get without early progress window
            String vanillaWindowTitle = "Minecraft* " + mcVersion;
            glfwWindowHintString(GLFW_X11_CLASS_NAME, vanillaWindowTitle);
            glfwWindowHintString(GLFW_X11_INSTANCE_NAME, vanillaWindowTitle);
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor == 0) {
            throw new IllegalStateException("Can't find a primary monitor");
        }
        GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);
        if (vidmode == null) {
            throw new IllegalStateException("Can't get a resolution");
        }
        long window = 0;
        var successfulWindow = new AtomicBoolean(false);
        var windowFailFuture = renderScheduler.schedule(() -> {
            if (!successfulWindow.get()) crashElegantly("Не удалось создать окно игры за 30 секунд.");
        }, 30, TimeUnit.SECONDS);
        int versidx = 0;
        var skipVersions = FMLConfig.<String>getListConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_SKIP_GL_VERSIONS);
        final String[] lastGLError = new String[GL_VERSIONS.length];
        do {
            final var glVersionToTry = GL_VERSIONS[versidx][0] + "." + GL_VERSIONS[versidx][1];
            if (skipVersions.contains(glVersionToTry)) {
                LOGGER.info("Skipping GL version " + glVersionToTry + " because of configuration");
                versidx++;
                continue;
            }
            LOGGER.info("Trying GL version " + glVersionToTry);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, GL_VERSIONS[versidx][0]);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, GL_VERSIONS[versidx][1]);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
            window = glfwCreateWindow(winWidth, winHeight, TITLE, 0L, 0L);
            var erridx = versidx;
            handleLastGLFWError((error, description) -> lastGLError[erridx] = String.format("Trying %d.%d: GLFW error: [0x%X]%s", GL_VERSIONS[erridx][0], GL_VERSIONS[erridx][1], error, description));
            if (lastGLError[versidx] != null) {
                LOGGER.trace(lastGLError[versidx]);
            }
            versidx++;
        } while (window == 0 && versidx < GL_VERSIONS.length);
        successfulWindow.set(true);
        windowFailFuture.cancel(true);
        if (window == 0) {
            throw new IllegalStateException("Failed to find a valid GLFW profile. We tried " +
                    Arrays.stream(GL_VERSIONS).map(p -> p[0] + "." + p[1]).filter(o -> !skipVersions.contains(o))
                            .collect(Collector.of(() -> new StringJoiner(", ").setEmptyValue("no versions"), StringJoiner::add, StringJoiner::merge, StringJoiner::toString))
                    + " but none of them worked.\n" + Arrays.stream(lastGLError).filter(Objects::nonNull).collect(Collectors.joining("\n")));
        }
        var requestedVersion = GL_VERSIONS[versidx - 1][0] + "." + GL_VERSIONS[versidx - 1][1];
        var maj = glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MAJOR);
        var min = glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MINOR);
        var gotVersion = maj + "." + min;
        LOGGER.info("Requested GL version " + requestedVersion + " got version " + gotVersion);
        this.glVersion = gotVersion;
        this.window = window;

        int[] x = new int[1];
        int[] y = new int[1];
        glfwGetMonitorPos(primaryMonitor, x, y);
        int monitorX = x[0];
        int monitorY = y[0];
        if (this.maximized) {
            glfwMaximizeWindow(window);
        }

        glfwGetWindowSize(window, x, y);
        this.winWidth = x[0];
        this.winHeight = y[0];

        glfwSetWindowPos(window, (vidmode.width() - this.winWidth) / 2 + monitorX, (vidmode.height() - this.winHeight) / 2 + monitorY);

        WindowIcon.apply(window);
        handleLastGLFWError((error, description) -> LOGGER.debug(String.format("Suppressing GLFW icon error: [0x%X]%s", error, description)));

        glfwSetFramebufferSizeCallback(window, this::fbResize);
        glfwSetWindowPosCallback(window, this::winMove);
        glfwSetWindowSizeCallback(window, this::winResize);

        glfwShowWindow(window);
        glfwGetWindowPos(window, x, y);
        handleLastGLFWError((error, description) -> LOGGER.debug(String.format("Suppressing GLFW get window position error: [0x%X]%s", error, description)));
        this.winX = x[0];
        this.winY = y[0];
        glfwGetFramebufferSize(window, x, y);
        this.fbWidth = x[0];
        this.fbHeight = y[0];
        glfwPollEvents();
    }

    private void winResize(long window, int width, int height) {
        if (window == this.window && width != 0 && height != 0) {
            this.winWidth = width;
            this.winHeight = height;
        }
    }

    private void fbResize(long window, int width, int height) {
        if (window == this.window && width != 0 && height != 0) {
            this.fbWidth = width;
            this.fbHeight = height;
        }
    }

    private void winMove(long window, int x, int y) {
        if (window == this.window) {
            this.winX = x;
            this.winY = y;
        }
    }

    private void handleLastGLFWError(BiConsumer<Integer, String> handler) {
        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
            int error = glfwGetError(pointerbuffer);
            if (error != GLFW_NO_ERROR) {
                long pDescription = pointerbuffer.get();
                String description = pDescription == 0L ? "" : MemoryUtil.memUTF8(pDescription);
                handler.accept(error, description);
            }
        }
    }

    /**
     * Передача окна игре: вызывается на главном потоке вместо создания окна Minecraft.
     */
    @Override
    public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitorSupplier) {
        try {
            this.initializationFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            Thread.dumpStack();
            crashElegantly("Окно игры не подготовилось за 30 секунд");
        }
        ImmediateWindowHandler.updateProgress("Initializing Game Graphics");
        while (!this.windowTick.isDone()) {
            this.windowTick.cancel(false);
        }
        var tries = 0;
        var renderlockticket = false;
        do {
            try {
                renderlockticket = renderLock.tryAcquire(100, TimeUnit.MILLISECONDS);
                if (++tries > 9) {
                    Thread.dumpStack();
                    crashElegantly("Не удалось передать окно игре за секунду");
                }
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        } while (!renderlockticket);
        renderLock.release();

        glfwMakeContextCurrent(window);
        glfwSetWindowTitle(window, TITLE);
        glfwSwapInterval(0);
        glfwSetFramebufferSizeCallback(window, null).free();
        glfwSetWindowPosCallback(window, null).free();
        glfwSetWindowSizeCallback(window, null).free();
        this.iconPending = true; // Minecraft поставит свою иконку сразу после передачи
        this.repaintTick = this::renderThreadFunc; // the repaint will continue to be called until the overlay takes over
        this.windowTick = null; // this tells the render thread that the async ticker is done
        return window;
    }

    @Override
    public boolean positionWindow(final Optional<Object> monitor, final IntConsumer widthSetter, final IntConsumer heightSetter, final IntConsumer xSetter, final IntConsumer ySetter) {
        widthSetter.accept(this.winWidth);
        heightSetter.accept(this.winHeight);
        xSetter.accept(this.winX);
        ySetter.accept(this.winY);
        return true;
    }

    @Override
    public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {
        width.accept(this.fbWidth);
        height.accept(this.fbHeight);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(final Supplier<?> mc, final Supplier<?> ri, final Consumer<Optional<Throwable>> ex, final boolean fade) {
        WindowIcon.apply(window);
        return () -> {
            try {
                return (T) vanillaOverlay.newInstance(mc.get(), ri.get(), ex, fade);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Не удалось создать экран загрузки Minecraft", e);
            }
        };
    }

    @Override
    public void updateModuleReads(final ModuleLayer layer) {
        var mc = layer.findModule("minecraft").orElseThrow();
        getClass().getModule().addReads(mc);
        var overlay = Class.forName(mc, "net.minecraft.client.gui.screens.LoadingOverlay");
        vanillaOverlay = overlay == null ? null : Arrays.stream(overlay.getConstructors()).filter(c -> c.getParameterCount() == 4).findFirst().orElse(null);
        if (vanillaOverlay == null) {
            throw new IllegalStateException("Не найден экран загрузки Minecraft");
        }
    }

    @Override
    public void periodicTick() {
        glfwPollEvents();
        if (iconPending) {
            iconPending = false;
            WindowIcon.apply(window);
        }
        repaintTick.run();
    }

    /**
     * Снимает окно и поток отрисовки — для отката на стандартный экран до передачи окна игре.
     */
    @Override
    public void close() {
        if (renderScheduler != null) {
            renderScheduler.shutdownNow();
            try {
                renderScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (window != 0) {
            glfwDestroyWindow(window);
            window = 0; // объекты GL уходят вместе с контекстом окна
        }
    }

    @Override
    public void crash(final String message) {
        crashElegantly(message);
    }
}
