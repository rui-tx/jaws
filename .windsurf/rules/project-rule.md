---
trigger: always_on
description:
globs:
---

# Project Rules

- Keep it Simple Stupid: If something is to be done, prefer simplicity over complex architeture. More lines of code can be good instead of complex abstractions. Only abstract when we see a need for it but always asks permission to do so.

- Modules: The core functionallity of this project is divided by components. Always try to make sure each module/component is responsible for the thing it's doing, always avoid cross responsibilities.

- Working code, then refactor: The first working code should be that, working code. We don't need fancy subsystems or super clever implementations for the problem at hand. We could always do that later.

- API design: Always follow the classic API design of Controller, Service, Repository with DTO, Model: A DTO should be responsible for its mapping to the model. A DTO should be responsible for its validation. A simple example is a login endpoint. There should be a DTO LoginRequest and LoginResponse, so, in this example, the request should be responsible for validation.

- Naming convenction: When creating a new component, use gods from Norse mythology that better reflect the function of the model with them. Everything else should be simple and concise

- In doubt ask, dont assume: Never assume something that you are not 100% certain that is what the user want. In doubt ask.

- Investigate, then plan: Never start coding the problem without investigating and making a plan. ALWAYS do this and only start coding when the user approves.

- Imports should always be on the top of the file, unless there is a name conflict.
