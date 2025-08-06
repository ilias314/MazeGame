import java.io.Serializable;

public class Cell implements Serializable {

    public final int x, y;
    public boolean visited;
    public final boolean[] walls;  // top, right, bottom, left

    public Cell(int x, int y) {
        this.x = x;
        this.y = y;
        this.visited = false;
        this.walls = new boolean[]{true, true, true, true};
    }
}
