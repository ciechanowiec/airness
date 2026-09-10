package eu.ciechanowiec.airness.spring;

record SpringStreamingResult(String status, String detail) {

    static SpringStreamingResult unsupported(String reason) {
        return new SpringStreamingResult("unsupported", reason);
    }

    String encoded(SpringStreamingInputs inputs, String application, String context) {
        return "streaming " + inputs.started() + " " + inputs.digest() + " "
            + SpringStreamingInput.encode(application) + " " + SpringStreamingInput.encode(context) + " "
            + this.status + " " + SpringStreamingInput.encode(this.detail);
    }
}
