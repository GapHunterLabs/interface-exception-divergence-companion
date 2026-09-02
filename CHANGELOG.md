<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Interface Exception Divergence Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Real Class Hierarchy Analysis (`ClassInheritorsSearch` over every
  real implementation of an interface) flagging a call through the
  interface type, wrapped in a broad silent catch, where implementations
  diverge on which RuntimeException they throw directly.

[Unreleased]: https://github.com/GapHunterLabs/interface-exception-divergence-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/interface-exception-divergence-companion/commits/0.1.0
