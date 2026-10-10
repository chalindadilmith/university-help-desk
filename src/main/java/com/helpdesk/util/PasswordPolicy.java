package com.helpdesk.util;

/** The one place that defines what an acceptable new password is. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;

    /** Same rule as a regular expression, for bean validation on forms. */
    public static final String REGEX = "^(?=.*\\p{L})(?=.*\\d).{" + MIN_LENGTH + "," + MAX_LENGTH + "}$";

    public static final String MESSAGE =
            "Password must be 8 to 100 characters and include at least one letter and one number.";

    private PasswordPolicy() { }

    /** Returns null when the password is acceptable, otherwise the message to show the user. */
    public static String check(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "The new password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters.";
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            return "The new password must include at least one letter and one number.";
        }
        return null;
    }
}
