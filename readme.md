# Readme.md
mvn -Pnative spring-boot:build-image

some info: https://docs.spring.io/spring-framework/reference/core/aot.html


## Payload contract (single shape for everything)

### `PUT /runtime/sessions/{sessionId}/groups/{groupNo}`

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_A",
      "fields": {
        "FIELD_A1": "x",
        "FIELD_A2": "y"
      }
    },
    {
      "formCode": "FORM_B",
      "fields": {
        "FIELD_A1": "z",
        "FIELD_B2": "w"
      }
    }
  ]
}
```

### Child-form group (Group 2: FORM_C → choose FORM_CB)

No separate “selection” key. The chosen child submission simply declares its parent:

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_CB",
      "parentFormCode": "FORM_C",
      "fields": {
        "FIELD_CB1": "salary"
      }
    }
  ]
}
```

### Combined Payload

Here, we combine the payload

```json
{
  "payloadVersion": 1,
  "submissions": [
    {
      "formCode": "FORM_A",
      "fields": {
        "FIELD_A1": "x",
        "FIELD_A2": "y"
      }
    },
    {
      "formCode": "FORM_B",
      "fields": {
        "FIELD_A1": "z",
        "FIELD_B2": "w"
      }
    },

    {
      "formCode": "FORM_CB",
      "parentFormCode": "FORM_C",
      "fields": {
        "FIELD_CB1": "salary"
      }
    }
  ]
}
```