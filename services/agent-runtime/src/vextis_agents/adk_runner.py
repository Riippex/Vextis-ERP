from google.adk.runners import InMemoryRunner


def enable_session_auto_creation(runner: InMemoryRunner) -> InMemoryRunner:
    """Configure an ephemeral ADK runner to create its requested session."""
    runner.auto_create_session = True
    return runner
