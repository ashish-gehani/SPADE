package spade.storage.quickstep;

/**
 * Exception class for a failure during Quickstep execution.
 */
public class QuickstepFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public QuickstepFailure(final String message) {
    super(message);
  }
}
