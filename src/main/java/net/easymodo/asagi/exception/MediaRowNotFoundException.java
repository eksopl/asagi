package net.easymodo.asagi.exception;

public class MediaRowNotFoundException extends Exception{
	private static final long serialVersionUID = 426343209141894583L;

	 public MediaRowNotFoundException() {
		 super("Media row not found for row inserted in post database");
	 }
}
