package application;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Card extends ImageView {
    /**
     * Responsible for storing the card's face-information (number and color)
     * It's constructor loads it's image depending on those two values
     * */

    private int number;
    private int color;

    public Card(int number, int color) {
	this.number = number;
	this.color = color;
	// Image image = new Image("images/00.jpg");
	if (number != -1) {
	    Image image = new Image("images/" + number + color + ".jpg");

	    setImage(image);
	    setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.8), 10, 0, 0, 0);");
	}
    }

    public int getNumber() {
	return number;
    }

    public void setNumber(int number) {
	this.number = number;
    }

    public int getColor() {
	return color;
    }

    public void setColor(int color) {
	this.color = color;
    }
}
