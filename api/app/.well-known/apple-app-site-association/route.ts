const appSiteAssociation = {
  applinks: {
    apps: [],
    details: [
      {
        appID: "QSZR732YXD.com.afterlifearchive.medmanager",
        paths: ["/auth/confirmed", "/auth/login"]
      }
    ]
  }
};

export function GET() {
  return Response.json(appSiteAssociation, {
    headers: {
      "content-type": "application/json"
    }
  });
}
