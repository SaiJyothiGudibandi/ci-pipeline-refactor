FROM python:3.8.2-alpine3.11

ENV FLASK_APP=flaskr
ENV FLASK_ENV=development

RUN mkdir /app
COPY build-info.yaml /app

WORKDIR /app

RUN pip install pytest

EXPOSE 5000

CMD [ "flask", "run", "--host=0.0.0.0" ]