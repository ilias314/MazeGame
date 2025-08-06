import java.io.Serializable;

public class Player implements Serializable {

    public int x, y;

    public Player(Cell entrance) {
        this.x = entrance.x;
        this.y = entrance.y;
    }
}
