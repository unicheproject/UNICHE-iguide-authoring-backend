# Wraps only commands verified on 2026-10-02 (AGENTS.md §6). A target that would need code is a gap in
# GAPS.md, not a placeholder here: `run` waits for G-012, `openapi` for G-004.

HEALTH_URL := http://localhost:8080/iguide/actuator/health

.PHONY: setup verify test infra-up infra-down health

## setup: create .env from .env.example if it does not exist yet
setup:
	@if [ -f .env ]; then echo ".env exists, left unchanged"; else cp .env.example .env && echo "created .env; set DB_PASSWORD"; fi

## verify: build and run every test (Testcontainers needs Docker)
verify:
	./mvnw -B verify

## test: unit and integration tests only
test:
	./mvnw -B test

## infra-up: build the image, start app and postgres, wait until the app is healthy
infra-up:
	docker compose up --build -d
	@for i in $$(seq 1 60); do curl -sf $(HEALTH_URL) >/dev/null && echo "app healthy" && exit 0; sleep 3; done; echo "app not healthy after 180 s"; docker compose logs --tail=50 app; exit 1

## infra-down: stop the stack and drop the database volume
infra-down:
	docker compose down -v

## health: query the actuator health endpoint of the running stack
health:
	@curl -sf $(HEALTH_URL) && echo
