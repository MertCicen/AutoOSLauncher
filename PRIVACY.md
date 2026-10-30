# Privacy notes

This document describes behavior found in the current AutoOS Launcher source. Review it again before using it as a public privacy policy.

- The launcher stores customization settings, selected app shortcuts, and local PIN settings on the device. Android backup is disabled for the application.
- Location permission is used to show GPS speed and trip information on screen. The source does not contain a developer analytics or location upload service.
- The app can display media information through Android's notification/media APIs when the user grants the relevant system access. Media artwork may be loaded from an HTTP or HTTPS URL supplied by another media app. That request goes to the URL's host and can expose the device's IP address to that host.
- The software EQ uses Android audio effect sessions. It does not record audio.
- The app contains no Google Play Billing, advertising SDK, analytics SDK, or user account system.

This is a source review, not a network traffic audit or a statement about data handled by other apps on the device.
