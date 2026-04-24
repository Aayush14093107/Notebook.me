// ════════════════════════════════════════════════════════
//  User-defined Exception
// ════════════════════════════════════════════════════════
class NotebookException extends Exception {
    private final String context;
    public NotebookException(String message, String context) {
        super(message);
        this.context = context;
    }
    public String getContext() { return context; }
    @Override
    public String toString() {
        return "[notebook.me] " + context + ": " + getMessage();
    }
}
