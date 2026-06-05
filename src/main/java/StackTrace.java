import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class StackTrace extends JPanel implements KeyListener, ActionListener {

    private static final int PANEL_WIDTH = 480, PANEL_HEIGHT = 520;
    private static final int BOX_X = 80, BOX_Y = 120, BOX_WIDTH = 320, BOX_HEIGHT = 300;
    private static final int MAX_BULLETS = 400;
    private static final double DUKE_RADIUS = 3.0;
    private static final double DUKE_SPEED = 3.4;
    private static final double GRAZE_BAND = 11.0;
    private static final int GRAZE_POINTS = 25;
    private static final int INDICATOR_FRAMES = 30;
    private static final int MAX_PENDING = 16;
    private static final int MAX_CATCHES = 3;
    private static final int INVULN_FRAMES = 48;
    private static final int MAX_PICKUPS = 8;
    private static final double PICKUP_SPEED = 1.6;
    private static final int HEAL_FLASH_FRAMES = 20;
    private static final int SHIELD_FLASH_FRAMES = 14;
    private static final int GRAZE_PER_HIT = 5;
    private static final int SLEEP_COST = 50;
    private static final int GC_COST = 100;
    private static final int SLEEP_FRAMES = 180;

    // Bullet kinds: 0 = semicolon (rain), 1 = null (aimed), 2 = brace (spiral + walls).
    private static final String[] GLYPHS = {";", "null", "{"};
    private static final int[] HALF_WIDTH = {4, 16, 4};
    private static final int[] HALF_HEIGHT = {7, 7, 7};

    private static final Color BACKGROUND = new Color(8, 8, 8);
    private static final Color FOREGROUND = new Color(210, 210, 210);
    private static final Color NOSE_RED = new Color(237, 28, 36);
    private static final Color DUKE_BLACK = new Color(20, 20, 20);
    private static final Color DUKE_GRAY = new Color(110, 110, 110);
    private static final Color CRASH_COLOR = new Color(220, 90, 80);
    private static final Color ERROR_RED = new Color(235, 45, 40);
    private static final Color HEAL_GREEN = new Color(90, 210, 110);
    private static final Color SHIELD_CYAN = new Color(120, 220, 235);
    private static final Color INDICATOR_COLOR = new Color(230, 120, 60);
    private static final Color SLEEP_BLUE = new Color(120, 160, 235);
    private static final Color GC_GREEN = new Color(90, 210, 130);

    // Duke as a pixel sprite: K = black, W = white, R = nose red, G = gray edge, . = clear.
    private static final int DUKE_PIXEL = 1;
    private static final String[] DUKE_SPRITE = {
            ".......G.......",
            "......GKG......",
            "......GKG......",
            ".....GKKKG.....",
            ".....GKKKG.....",
            "....GKKKKKG....",
            "....GKKKKKG....",
            "...GKKKKKKKG...",
            "...GKKRRRKKG...",
            "..GKKRRRRRKKG..",
            "..GKRRRRRRRKG..",
            "..GKKRRRRRKKG..",
            ".GKWWRRRWWWKG..",
            ".GKWWWWWWWWKG..",
            "GKWWWWWWWWWWKG.",
            "GKWWWWWWWWWWKG.",
            "GKWWWWWWWWWWKG.",
            "GKWWWWWWWWWWKG.",
            ".GKWWWWWWWWKG..",
            ".GKWWWGGWWWKG..",
            ".GKWKGG.GGKWKG.",
            ".GKKG....GGKKG.",
    };

    // Bullet pool as parallel arrays (structure of arrays, swap-remove on cull).
    private final double[] positionX = new double[MAX_BULLETS];
    private final double[] positionY = new double[MAX_BULLETS];
    private final double[] velocityX = new double[MAX_BULLETS];
    private final double[] velocityY = new double[MAX_BULLETS];
    private final int[] kind = new int[MAX_BULLETS];
    private final boolean[] grazed = new boolean[MAX_BULLETS];
    private final boolean[] deadly = new boolean[MAX_BULLETS];
    private int bulletCount;

    // Pending attacks: an indicator window before bullets actually spawn.
    // pendingType: 1 = aimed null, 2 = spiral ring. Geometry is locked at queue time.
    private final int[] pendingType = new int[MAX_PENDING];
    private final int[] pendingTimer = new int[MAX_PENDING];
    private final double[] pendingX = new double[MAX_PENDING];
    private final double[] pendingY = new double[MAX_PENDING];
    private final double[] pendingDirX = new double[MAX_PENDING];
    private final double[] pendingDirY = new double[MAX_PENDING];
    private final double[] pendingSpeed = new double[MAX_PENDING];
    private int pendingCount;

    // Falling pickups. pickupType: 0 = catch (+1 max capacity), 1 = finally (shield).
    private final int[] pickupType = new int[MAX_PICKUPS];
    private final double[] pickupX = new double[MAX_PICKUPS];
    private final double[] pickupY = new double[MAX_PICKUPS];
    private int pickupCount;

    private double dukeX, dukeY;
    private boolean movingUp, movingDown, movingLeft, movingRight;

    private long score;
    private long framesSurvived;
    private int level;
    private int catches;
    private int maxCatches;
    private boolean shielded;
    private int healFlash;
    private int shieldFlash;
    private int attackCount;
    private int invuln;
    private boolean grazing;
    private int grazeMeter;
    private int lastAbility;
    private int slowTimer;
    private int gcFlash;
    private int spawnCooldown;
    private double spiralAngle;
    private boolean gameOver;
    private int deathKind;
    private boolean deathByError;

    private long randomState = System.nanoTime();

    public StackTrace() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(BACKGROUND);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    private void reset() {
        bulletCount = 0;
        pendingCount = 0;
        dukeX = BOX_X + BOX_WIDTH / 2.0;
        dukeY = BOX_Y + BOX_HEIGHT / 2.0;
        score = 0;
        framesSurvived = 0;
        level = 1;
        maxCatches = MAX_CATCHES;
        catches = MAX_CATCHES;
        shielded = false;
        healFlash = 0;
        shieldFlash = 0;
        attackCount = 0;
        invuln = 0;
        grazing = false;
        grazeMeter = 0;
        lastAbility = 0;
        slowTimer = 0;
        gcFlash = 0;
        pickupCount = 0;
        spawnCooldown = 36;
        spiralAngle = 0;
        gameOver = false;
        movingUp = movingDown = movingLeft = movingRight = false;
    }

    // xorshift64 -> [0, 1). The 0x1.0p-53 multiply maps the top 53 bits to a unit double.
    private double nextRandom() {
        randomState ^= randomState << 13;
        randomState ^= randomState >>> 7;
        randomState ^= randomState << 17;
        return (randomState >>> 11) * 0x1.0p-53;
    }

    // Quadratic term stretches later levels.
    private long scoreForLevel(int n) {
        return (n - 1) * 1000L + (long) (n - 1) * (n - 1) * 250L;
    }

    // Floored above INDICATOR_FRAMES so cues stay readable at high levels.
    private int cooldownForLevel() {
        int frames = (int) (48 - 6 * (Math.log(level + 1) / Math.log(2)));
        return Math.max(INDICATOR_FRAMES + 8, frames);
    }

    // +1 at doubling thresholds (5, 10, 20, ...); keeps simultaneous threats dodgeable.
    private int threatCap() {
        if (level < 5) {
            return 3;
        }
        return 3 + (int) (Math.log(level / 5.0) / Math.log(2)) + 1;
    }

    // Rain has no pending phase, so count its live bullets toward the cap directly;
    // otherwise a braces wall plus an unwarned semicolon in the same row is undodgeable.
    private int liveRainGroups() {
        int n = 0;
        for (int i = 0; i < bulletCount; i++) {
            if (kind[i] == 0) {
                n++;
            }
        }
        return n;
    }

    // Red = unrecoverable Error (instakill, ignores catches).
    private double redChance() {
        return Math.min(0.50, 0.05 + 0.07 * (Math.log(level) / Math.log(2)));
    }

    private void addBullet(double x, double y, double vx, double vy, int bulletKind) {
        if (bulletCount >= MAX_BULLETS) {
            return;
        }
        int index = bulletCount++;
        positionX[index] = x;
        positionY[index] = y;
        velocityX[index] = vx;
        velocityY[index] = vy;
        kind[index] = bulletKind;
        grazed[index] = false;
        deadly[index] = nextRandom() < redChance();
    }

    private void removeBullet(int index) {
        int last = --bulletCount;
        positionX[index] = positionX[last];
        positionY[index] = positionY[last];
        velocityX[index] = velocityX[last];
        velocityY[index] = velocityY[last];
        kind[index] = kind[last];
        grazed[index] = grazed[last];
        deadly[index] = deadly[last];
    }

    // ability: 1 = Thread.sleep (slow), 2 = System.gc (clear).
    private void fireAbility(int ability) {
        if (gameOver || lastAbility == ability) {
            return;
        }
        if (ability == 1 && grazeMeter >= SLEEP_COST) {
            grazeMeter -= SLEEP_COST;
            slowTimer = SLEEP_FRAMES;
            lastAbility = 1;
        } else if (ability == 2 && grazeMeter >= GC_COST) {
            grazeMeter -= GC_COST;
            bulletCount = 0;
            gcFlash = SHIELD_FLASH_FRAMES;
            lastAbility = 2;
        }
    }

    private void spawnPickup() {
        if (pickupCount >= MAX_PICKUPS) {
            return;
        }
        int index = pickupCount++;
        pickupType[index] = nextRandom() < 0.7 ? 0 : 1;
        pickupX[index] = BOX_X + 20 + nextRandom() * (BOX_WIDTH - 40);
        pickupY[index] = BOX_Y - 12;
    }

    private void removePickup(int index) {
        int last = --pickupCount;
        pickupType[index] = pickupType[last];
        pickupX[index] = pickupX[last];
        pickupY[index] = pickupY[last];
    }

    private void queueAttack(int type, double x, double y, double dirX, double dirY, double speed) {
        if (pendingCount >= MAX_PENDING) {
            return;
        }
        int index = pendingCount++;
        pendingType[index] = type;
        pendingTimer[index] = INDICATOR_FRAMES;
        pendingX[index] = x;
        pendingY[index] = y;
        pendingDirX[index] = dirX;
        pendingDirY[index] = dirY;
        pendingSpeed[index] = speed;
    }

    private void removePending(int index) {
        int last = --pendingCount;
        pendingType[index] = pendingType[last];
        pendingTimer[index] = pendingTimer[last];
        pendingX[index] = pendingX[last];
        pendingY[index] = pendingY[last];
        pendingDirX[index] = pendingDirX[last];
        pendingDirY[index] = pendingDirY[last];
        pendingSpeed[index] = pendingSpeed[last];
    }

    private void firePending(int index) {
        if (pendingType[index] == 1) {
            addBullet(pendingX[index], pendingY[index],
                    pendingDirX[index] * pendingSpeed[index],
                    pendingDirY[index] * pendingSpeed[index], 1);
        } else if (pendingType[index] == 2) {
            int arms = 5;
            for (int a = 0; a < arms; a++) {
                double angle = spiralAngle + a * (Math.PI * 2 / arms);
                addBullet(pendingX[index], pendingY[index],
                        Math.cos(angle) * pendingSpeed[index],
                        Math.sin(angle) * pendingSpeed[index], 2);
            }
            spiralAngle += 0.5;
        } else {
            double gapY = pendingY[index];
            double speed = pendingSpeed[index];
            int rows = BOX_HEIGHT / 20;
            for (int r = 0; r <= rows; r++) {
                double y = BOX_Y + r * 20.0;
                if (Math.abs(y - gapY) < 26) {
                    continue;
                }
                addBullet(BOX_X - 8, y, speed, 0, 2);
                addBullet(BOX_X + BOX_WIDTH + 8, y, -speed, 0, 2);
            }
        }
    }

    private void spawnPattern() {
        double speed = 2.2 + level * 0.25;
        double pick = nextRandom();
        if (pick < 0.38) {
            int count = 1 + (int) (nextRandom() * 3);
            for (int n = 0; n < count; n++) {
                double x = BOX_X + nextRandom() * BOX_WIDTH;
                addBullet(x, BOX_Y - 12, (nextRandom() - 0.5) * 0.8, speed, 0);
            }
        } else if (pick < 0.64) {
            double originX, originY;
            int edge = (int) (nextRandom() * 4);
            if (edge == 0) {
                originX = BOX_X - 12;
                originY = BOX_Y + nextRandom() * BOX_HEIGHT;
            } else if (edge == 1) {
                originX = BOX_X + BOX_WIDTH + 12;
                originY = BOX_Y + nextRandom() * BOX_HEIGHT;
            } else if (edge == 2) {
                originX = BOX_X + nextRandom() * BOX_WIDTH;
                originY = BOX_Y - 12;
            } else {
                originX = BOX_X + nextRandom() * BOX_WIDTH;
                originY = BOX_Y + BOX_HEIGHT + 12;
            }
            double towardX = dukeX - originX;
            double towardY = dukeY - originY;
            double length = Math.sqrt(towardX * towardX + towardY * towardY);
            if (length < 1.0) {
                length = 1.0;
            }
            // Aim is locked here, at queue time, not recomputed on fire so the indicator line is honest and the player can juke out of it.
            queueAttack(1, originX, originY, towardX / length, towardY / length, speed + 1.0);
        } else if (pick < 0.84) {
            double originX = BOX_X + 40 + nextRandom() * (BOX_WIDTH - 80);
            double originY = BOX_Y + 40 + nextRandom() * (BOX_HEIGHT - 80);
            queueAttack(2, originX, originY, 0, 0, speed);
        } else {
            // Gap center stored in pendingY so the indicator can show the safe row.
            double gapY = BOX_Y + 40 + nextRandom() * (BOX_HEIGHT - 80);
            queueAttack(3, 0, gapY, 0, 0, speed * 0.7);
        }
    }

    private void update() {
        if (gameOver) {
            return;
        }
        framesSurvived++;
        score++;
        if (score >= scoreForLevel(level + 1)) {
            level++;
            if (catches < maxCatches) {
                catches++;
                healFlash = HEAL_FLASH_FRAMES;
            }
        }
        if (invuln > 0) {
            invuln--;
        }
        if (healFlash > 0) {
            healFlash--;
        }
        if (shieldFlash > 0) {
            shieldFlash--;
        }
        if (slowTimer > 0) {
            slowTimer--;
        }
        if (gcFlash > 0) {
            gcFlash--;
        }

        if (movingUp) {
            dukeY -= DUKE_SPEED;
        }
        if (movingDown) {
            dukeY += DUKE_SPEED;
        }
        if (movingLeft) {
            dukeX -= DUKE_SPEED;
        }
        if (movingRight) {
            dukeX += DUKE_SPEED;
        }
        if (dukeX < BOX_X + DUKE_RADIUS) {
            dukeX = BOX_X + DUKE_RADIUS;
        }
        if (dukeX > BOX_X + BOX_WIDTH - DUKE_RADIUS) {
            dukeX = BOX_X + BOX_WIDTH - DUKE_RADIUS;
        }
        if (dukeY < BOX_Y + DUKE_RADIUS) {
            dukeY = BOX_Y + DUKE_RADIUS;
        }
        if (dukeY > BOX_Y + BOX_HEIGHT - DUKE_RADIUS) {
            dukeY = BOX_Y + BOX_HEIGHT - DUKE_RADIUS;
        }

        int index = 0;
        grazing = false;
        double timeScale = slowTimer > 0 ? 0.25 : 1.0;
        while (index < bulletCount) {
            positionX[index] += velocityX[index] * timeScale;
            positionY[index] += velocityY[index] * timeScale;

            if (positionX[index] < BOX_X - 40 || positionX[index] > BOX_X + BOX_WIDTH + 40
                    || positionY[index] < BOX_Y - 40 || positionY[index] > BOX_Y + BOX_HEIGHT + 40) {
                removeBullet(index);
                continue;
            }

            double distanceX = Math.abs(dukeX - positionX[index]);
            double distanceY = Math.abs(dukeY - positionY[index]);
            double reachX = HALF_WIDTH[kind[index]] + DUKE_RADIUS;
            double reachY = HALF_HEIGHT[kind[index]] + DUKE_RADIUS;

            if (distanceX < reachX && distanceY < reachY) {
                if (invuln <= 0) {
                    boolean lethal = deadly[index] || catches <= 1;
                    if (lethal && shielded) {
                        shielded = false;
                        shieldFlash = SHIELD_FLASH_FRAMES;
                        invuln = INVULN_FRAMES;
                        removeBullet(index);
                        continue;
                    }
                    if (deadly[index]) {
                        gameOver = true;
                        deathKind = kind[index];
                        deathByError = true;
                    } else if (--catches <= 0) {
                        gameOver = true;
                        deathKind = kind[index];
                        deathByError = false;
                    } else {
                        invuln = INVULN_FRAMES;
                        removeBullet(index);
                        continue;
                    }
                }
            } else if (distanceX < reachX + GRAZE_BAND && distanceY < reachY + GRAZE_BAND) {
                grazing = true;
                if (!grazed[index]) {
                    grazed[index] = true;
                    score += GRAZE_POINTS;
                    if (grazeMeter < GC_COST) {
                        grazeMeter = Math.min(GC_COST, grazeMeter + GRAZE_PER_HIT);
                    }
                }
            }
            index++;
        }

        int pendingIndex = 0;
        while (pendingIndex < pendingCount) {
            if (--pendingTimer[pendingIndex] <= 0) {
                firePending(pendingIndex);
                removePending(pendingIndex);
                continue;
            }
            pendingIndex++;
        }

        int pickupIndex = 0;
        while (pickupIndex < pickupCount) {
            pickupY[pickupIndex] += PICKUP_SPEED;
            double reachX = DUKE_SPRITE[0].length() * DUKE_PIXEL / 2.0 + 12;
            double reachY = DUKE_SPRITE.length * DUKE_PIXEL / 2.0 + 12;
            if (Math.abs(dukeX - pickupX[pickupIndex]) < reachX
                    && Math.abs(dukeY - pickupY[pickupIndex]) < reachY) {
                if (pickupType[pickupIndex] == 0) {
                    maxCatches++;
                } else {
                    shielded = true;
                    shieldFlash = SHIELD_FLASH_FRAMES;
                }
                removePickup(pickupIndex);
                continue;
            }
            if (pickupY[pickupIndex] > BOX_Y + BOX_HEIGHT + 20) {
                removePickup(pickupIndex);
                continue;
            }
            pickupIndex++;
        }

        if (slowTimer <= 0 && --spawnCooldown <= 0) {
            if (pendingCount + liveRainGroups() < threatCap()) {
                spawnPattern();
            }
            spawnCooldown = cooldownForLevel();
            attackCount++;
            if (attackCount % 10 == 0 && nextRandom() < 0.10) {
                spawnPickup();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        // Scale the logical PANEL_WIDTH x PANEL_HEIGHT canvas to fill the window preserving aspect ratio and centering (letterbox stays background-black).
        Graphics2D g2 = (Graphics2D) graphics;
        double scale = Math.min(getWidth() / (double) PANEL_WIDTH, getHeight() / (double) PANEL_HEIGHT);
        g2.translate((getWidth() - PANEL_WIDTH * scale) / 2, (getHeight() - PANEL_HEIGHT * scale) / 2);
        g2.scale(scale, scale);

        graphics.setColor(FOREGROUND);
        graphics.drawRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT);
        graphics.drawString("try {", BOX_X, BOX_Y - 8);
        graphics.drawString("}", BOX_X, BOX_Y + BOX_HEIGHT + 20);
        graphics.drawString("score " + score, BOX_X, 36);
        graphics.drawString("LV " + level, BOX_X + BOX_WIDTH - 48, 36);
        graphics.setColor(healFlash > 0 ? HEAL_GREEN : FOREGROUND);
        graphics.drawString("catch " + catches + "/" + maxCatches, BOX_X + BOX_WIDTH / 2 - 28, 36);
        if (shielded) {
            graphics.setColor(SHIELD_CYAN);
            graphics.drawString("finally", BOX_X + BOX_WIDTH / 2 - 22, 52);
        }

        int barWidth = BOX_WIDTH * grazeMeter / GC_COST;
        graphics.setColor(DUKE_GRAY);
        graphics.drawString("GRAZE " + grazeMeter + "/" + GC_COST, BOX_X, BOX_Y + BOX_HEIGHT + 52);
        graphics.drawRect(BOX_X, BOX_Y + BOX_HEIGHT + 58, BOX_WIDTH, 8);
        graphics.setColor(grazeMeter >= GC_COST ? GC_GREEN
                : grazeMeter >= SLEEP_COST ? SLEEP_BLUE : DUKE_GRAY);
        graphics.fillRect(BOX_X, BOX_Y + BOX_HEIGHT + 58, barWidth, 8);

        boolean sleepReady = grazeMeter >= SLEEP_COST && lastAbility != 1;
        boolean gcReady = grazeMeter >= GC_COST && lastAbility != 2;
        graphics.setColor(sleepReady ? SLEEP_BLUE : DUKE_GRAY);
        graphics.drawString("[Q] sleep 50", BOX_X, BOX_Y + BOX_HEIGHT + 78);
        graphics.setColor(gcReady ? GC_GREEN : DUKE_GRAY);
        graphics.drawString("[E] gc 100", BOX_X + BOX_WIDTH - 96, BOX_Y + BOX_HEIGHT + 78);

        for (int i = 0; i < pickupCount; i++) {
            graphics.setColor(pickupType[i] == 0 ? HEAL_GREEN : SHIELD_CYAN);
            graphics.drawString(pickupType[i] == 0 ? "+catch" : "finally",
                    (int) pickupX[i] - 18, (int) pickupY[i] + 5);
        }

        for (int i = 0; i < bulletCount; i++) {
            graphics.setColor(deadly[i] ? ERROR_RED : FOREGROUND);
            graphics.drawString(GLYPHS[kind[i]],
                    (int) (positionX[i] - HALF_WIDTH[kind[i]]),
                    (int) (positionY[i] + 5));
        }

        graphics.setColor(INDICATOR_COLOR);
        for (int i = 0; i < pendingCount; i++) {
            int px = (int) pendingX[i];
            int py = (int) pendingY[i];
            if (pendingType[i] == 1) {
                int farX = px + (int) (pendingDirX[i] * 600);
                int farY = py + (int) (pendingDirY[i] * 600);
                graphics.drawLine(px, py, farX, farY);
                graphics.drawString("NullPointerException", px - 60, py - 6);
            } else if (pendingType[i] == 2) {
                int radius = 6 + (INDICATOR_FRAMES - pendingTimer[i]) / 2;
                graphics.drawOval(px - radius, py - radius, radius * 2, radius * 2);
                graphics.drawString("StackOverflowError", px - 50, py - radius - 6);
            } else {
                graphics.drawLine(BOX_X, py - 26, BOX_X + 14, py - 26);
                graphics.drawLine(BOX_X, py + 26, BOX_X + 14, py + 26);
                graphics.drawLine(BOX_X + BOX_WIDTH - 14, py - 26, BOX_X + BOX_WIDTH, py - 26);
                graphics.drawLine(BOX_X + BOX_WIDTH - 14, py + 26, BOX_X + BOX_WIDTH, py + 26);
                graphics.drawString("} mismatched {", BOX_X + BOX_WIDTH / 2 - 44, py + 4);
            }
        }

        int spriteWidth = DUKE_SPRITE[0].length();
        int originX = (int) dukeX - spriteWidth * DUKE_PIXEL / 2;
        int originY = (int) dukeY - DUKE_SPRITE.length * DUKE_PIXEL / 2;
        boolean blinkOff = invuln > 0 && (invuln / 6) % 2 == 0;
        if (!blinkOff) {
            for (int row = 0; row < DUKE_SPRITE.length; row++) {
                String line = DUKE_SPRITE[row];
                for (int col = 0; col < spriteWidth; col++) {
                    char cell = line.charAt(col);
                    if (cell == '.') {
                        continue;
                    }
                    graphics.setColor(cell == 'R' ? NOSE_RED
                            : cell == 'K' ? DUKE_BLACK
                              : cell == 'G' ? DUKE_GRAY
                                : Color.WHITE);
                    graphics.fillRect(originX + col * DUKE_PIXEL, originY + row * DUKE_PIXEL, DUKE_PIXEL, DUKE_PIXEL);
                }
            }
            if (grazing) {
                graphics.setColor(Color.WHITE);
                int[] dRow = {-1, 1, 0, 0};
                int[] dCol = {0, 0, -1, 1};
                for (int row = 0; row < DUKE_SPRITE.length; row++) {
                    for (int col = 0; col < spriteWidth; col++) {
                        if (DUKE_SPRITE[row].charAt(col) == '.') {
                            continue;
                        }
                        for (int n = 0; n < 4; n++) {
                            int nr = row + dRow[n];
                            int nc = col + dCol[n];
                            boolean empty = nr < 0 || nr >= DUKE_SPRITE.length
                                    || nc < 0 || nc >= spriteWidth
                                    || DUKE_SPRITE[nr].charAt(nc) == '.';
                            if (empty) {
                                graphics.fillRect(originX + nc * DUKE_PIXEL,
                                        originY + nr * DUKE_PIXEL, DUKE_PIXEL, DUKE_PIXEL);
                            }
                        }
                    }
                }
            }
        }

        if (shielded) {
            graphics.setColor(SHIELD_CYAN);
            int ringR = spriteWidth * DUKE_PIXEL;
            graphics.drawOval((int) dukeX - ringR, (int) dukeY - ringR, ringR * 2, ringR * 2);
        }

        if (slowTimer > 0) {
            graphics.setColor(new Color(SLEEP_BLUE.getRed(), SLEEP_BLUE.getGreen(), SLEEP_BLUE.getBlue(), 28));
            graphics.fillRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT);
        }

        if (gcFlash > 0) {
            int alpha = 170 * gcFlash / SHIELD_FLASH_FRAMES;
            graphics.setColor(new Color(GC_GREEN.getRed(), GC_GREEN.getGreen(), GC_GREEN.getBlue(), alpha));
            graphics.fillRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT);
        }

        if (shieldFlash > 0) {
            int alpha = 180 * shieldFlash / SHIELD_FLASH_FRAMES;
            graphics.setColor(new Color(120, 220, 235, alpha));
            graphics.fillRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT);
        }

        if (gameOver) {
            graphics.setColor(new Color(0, 0, 0, 170));
            graphics.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

            String thrown;
            if (deathByError) {
                thrown = deathKind == 2 ? "java.lang.StackOverflowError"
                        : deathKind == 0 ? "java.lang.AssertionError"
                          : "java.lang.VirtualMachineError";
            } else {
                thrown = deathKind == 1 ? "java.lang.NullPointerException"
                        : deathKind == 2 ? "java.lang.IllegalStateException"
                          : "java.lang.RuntimeException";
            }

            graphics.setColor(CRASH_COLOR);
            graphics.drawString("Exception in thread \"main\" " + thrown, 40, 150);
            graphics.drawString("    at Duke.dodge(StackTrace.java:" + level + ")", 40, 172);
            graphics.drawString("    at Duke.main(StackTrace.java)", 40, 190);
            graphics.setColor(DUKE_GRAY);
            graphics.drawString(deathByError
                    ? "// fatal: an Error cannot be caught"
                    : "// out of catches: exception escaped the try", 40, 216);
            graphics.setColor(FOREGROUND);
            graphics.drawString("survived to LV " + level + "   score " + score, 40, 248);
            graphics.drawString("press R to recompile", 40, 270);
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_W) {
            movingUp = true;
        } else if (code == KeyEvent.VK_S) {
            movingDown = true;
        } else if (code == KeyEvent.VK_A) {
            movingLeft = true;
        } else if (code == KeyEvent.VK_D) {
            movingRight = true;
        } else if (code == KeyEvent.VK_Q) {
            fireAbility(1);
        } else if (code == KeyEvent.VK_E) {
            fireAbility(2);
        } else if (code == KeyEvent.VK_R && gameOver) {
            reset();
        } else if (code == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_W) {
            movingUp = false;
        } else if (code == KeyEvent.VK_S) {
            movingDown = false;
        } else if (code == KeyEvent.VK_A) {
            movingLeft = false;
        } else if (code == KeyEvent.VK_D) {
            movingRight = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent event) {
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        update();
        repaint();
    }

    void main() {
        JFrame frame = new JFrame("Stack Trace");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.add(this);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        requestFocusInWindow();
        new Timer(16, this).start();
    }
}