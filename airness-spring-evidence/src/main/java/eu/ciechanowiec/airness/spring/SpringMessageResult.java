package eu.ciechanowiec.airness.spring;

/**
 * One outcome without the translated text or application data.
 *
 * @param status assessed, missing, unassessed or error
 * @param detail the count, reference or reason
 */
record SpringMessageResult(String status, String detail) {

    static SpringMessageResult unassessed(String reason) {
        return new SpringMessageResult("unassessed", reason);
    }
}
