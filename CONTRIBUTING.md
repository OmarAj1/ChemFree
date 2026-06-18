# Contributing to Purely

First off, thank you for considering contributing to Purely! People like you make this project better for everyone.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check if there's already an active issue. If not, feel free to open one!

## Fork & create a branch

If this is something you think you can fix, then [fork](https://help.github.com/articles/fork-a-repo) the repository and create a branch with a descriptive name.

A good branch name would be (where issue #325 is the ticket you're working on):

```sh
git checkout -b 325-add-new-dietary-flag
```

## Implementation Guidelines

- **Kotlin First**: All new files should be written in modern Kotlin.
- **Jetpack Compose**: Follow Material 3 design guidelines. Do not submit XML layouts.
- **Testing**: Ensure you write tests for any logic you implement, especially regarding database interactions and chemical matching.
- **Commit Messages**: Write clear, concise commit messages.

## Submitting your Pull Request

1. Push your branch to your fork.
2. Open a Pull Request from your fork to the `main` branch of this repository.
3. Describe your changes detailedly and link to the issue it resolves.
4. Wait for maintainers to review your code. We may request some changes before merging.

Thank you!
