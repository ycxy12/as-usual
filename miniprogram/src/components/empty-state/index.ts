Component({
  properties: {
    title: { type: String, value: "" },
    detail: { type: String, value: "" },
    action: { type: String, value: "" },
  },
  methods: {
    onAction() {
      this.triggerEvent("action");
    },
  },
});
