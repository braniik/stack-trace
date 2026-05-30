import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
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

    // Bullet kinds: 0 = semicolon (rain), 1 = null (aimed NullPointer).
    private static final String[] GLYPHS = {";", "null"};
    private static final int[] HALF_WIDTH = {4, 16};
    private static final int[] HALF_HEIGHT = {7, 7};

    private static final Color BACKGROUND = new Color(8, 8, 8);
    private static final Color FOREGROUND = new Color(210, 210, 210);
    private static final Color DUKE_COLOR = Color.WHITE;
    private static final Color CRASH_COLOR = new Color(220, 90, 80);

    // Bullet pool as parallel arrays.
    private final double[] positionX = new double[MAX_BULLETS];
    private final double[] positionY = new double[MAX_BULLETS];
    private final double[] velocityX = new double[MAX_BULLETS];
    private final double[] velocityY = new double[MAX_BULLETS];
    private final int[] kind = new int[MAX_BULLETS];
    private final boolean[] grazed = new boolean[MAX_BULLETS];
    private int bulletCount;

    private double dukeX, dukeY;
    private boolean movingUp, movingDown, movingLeft, movingRight;

    private long score;
    private long framesSurvived;
    private int spawnCooldown;
    private boolean gameOver;

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
        dukeX = BOX_X + BOX_WIDTH / 2.0;
        dukeY = BOX_Y + BOX_HEIGHT / 2.0;
        score = 0;
        framesSurvived = 0;
        spawnCooldown = 36;
        gameOver = false;
        movingUp = movingDown = movingLeft = movingRight = false;
    }

    // xorshift64 -> [0, 1)
    private double nextRandom() {
        randomState ^= randomState << 13;
        randomState ^= randomState >>> 7;
        randomState ^= randomState << 17;
        return (randomState >>> 11) * 0x1.0p-53;
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
    }

    private void removeBullet(int index) {
        int last = --bulletCount;
        positionX[index] = positionX[last];
        positionY[index] = positionY[last];
        velocityX[index] = velocityX[last];
        velocityY[index] = velocityY[last];
        kind[index] = kind[last];
        grazed[index] = grazed[last];
    }

    private void spawnPattern() {
        double difficulty = framesSurvived / 600.0;
        double speed = 2.2 + difficulty;
        if (nextRandom() < 0.55) {
            int count = 1 + (int) (nextRandom() * 3);
            for (int n = 0; n < count; n++) {
                double x = BOX_X + nextRandom() * BOX_WIDTH;
                addBullet(x, BOX_Y - 12, (nextRandom() - 0.5) * 0.8, speed, 0);
            }
        } else {
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
            double aimedSpeed = speed + 1.0;
            addBullet(originX, originY, towardX / length * aimedSpeed, towardY / length * aimedSpeed, 1);
        }
    }

    private void update() {
        if (gameOver) {
            return;
        }
        framesSurvived++;
        score++;

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
        while (index < bulletCount) {
            positionX[index] += velocityX[index];
            positionY[index] += velocityY[index];

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
                gameOver = true;
            } else if (!grazed[index]
                    && distanceX < reachX + GRAZE_BAND && distanceY < reachY + GRAZE_BAND) {
                grazed[index] = true;
                score += GRAZE_POINTS;
            }
            index++;
        }

        if (--spawnCooldown <= 0) {
            spawnPattern();
            spawnCooldown = Math.max(6, 28 - (int) (framesSurvived / 150));
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        graphics.setColor(FOREGROUND);
        graphics.drawRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT);
        graphics.drawString("try {", BOX_X, BOX_Y - 8);
        graphics.drawString("}", BOX_X, BOX_Y + BOX_HEIGHT + 20);
        graphics.drawString("score " + score, BOX_X, 36);

        for (int i = 0; i < bulletCount; i++) {
            graphics.drawString(GLYPHS[kind[i]],
                    (int) (positionX[i] - HALF_WIDTH[kind[i]]),
                    (int) (positionY[i] + 5));
        }

        int wedgeX = (int) dukeX;
        int wedgeY = (int) dukeY;
        int[] wedgeXs = {wedgeX, wedgeX - 7, wedgeX + 7};
        int[] wedgeYs = {wedgeY - 9, wedgeY + 8, wedgeY + 8};
        graphics.setColor(DUKE_COLOR);
        graphics.fillPolygon(wedgeXs, wedgeYs, 3);

        if (gameOver) {
            graphics.setColor(new Color(0, 0, 0, 170));
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(CRASH_COLOR);
            graphics.drawString("Exception in thread \"main\"", 40, 150);
            graphics.drawString("java.lang.NullPointerException", 40, 170);
            graphics.drawString("    at Duke.dodge(StackTrace.java)", 40, 192);
            graphics.drawString("    at Duke.main(StackTrace.java)", 40, 210);
            graphics.setColor(FOREGROUND);
            graphics.drawString("score " + score, 40, 244);
            graphics.drawString("press R to recompile", 40, 266);
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
        } else if (code == KeyEvent.VK_R && gameOver) {
            reset();
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
        frame.setResizable(false);
        frame.add(this);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        requestFocusInWindow();
        new Timer(16, this).start();
    }
}